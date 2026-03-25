from __future__ import annotations

import math
from datetime import datetime
from typing import Any, TypedDict

from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import RunnableLambda
from langgraph.graph import END, START, StateGraph

from models import AIInsightResponse, OrderAnalysisResponse
from service_clients import AnalyticsApiClient


ACTION_PLAYBOOK: dict[str, list[str]] = {
    "PRODUCTION_COMPLETED": [
        "Confirm the factory completion plan and recover any open material or capacity constraints.",
        "Escalate the supplier for a revised production finish date with a daily commit.",
        "Pre-book outbound shipping capacity so the order can move immediately after production closes.",
    ],
    "SHIPPED": [
        "Validate vessel booking, export release, and container handoff with the freight forwarder.",
        "Check whether shipping documents or pickup instructions are blocking departure.",
        "Escalate the logistics provider for a confirmed sail date and exception owner.",
    ],
    "ARRIVED_PORT": [
        "Confirm port arrival visibility and terminal handling status with the carrier.",
        "Review port congestion or transshipment delays that could block discharge.",
        "Align inland planning so customs paperwork is ready before cargo release.",
    ],
    "CUSTOMS_CLEARED": [
        "Verify broker documentation completeness and resolve any customs hold immediately.",
        "Confirm tariff, invoice, and packing-list accuracy against the shipment record.",
        "Set a same-day follow-up cadence with the customs broker until release is confirmed.",
    ],
    "DELIVERED": [
        "Confirm final-mile carrier slot availability and receiving-site readiness.",
        "Validate proof-of-delivery requirements and unloading windows with the customer.",
        "Escalate any delivery appointment misses with the local transport partner.",
    ],
}

SHIPMENT_RELATED_STAGES = {"SHIPPED", "ARRIVED_PORT", "CUSTOMS_CLEARED"}
COST_SPIKE_RATIO_THRESHOLD = 1.5


class AnalysisState(TypedDict, total=False):
    mode: str
    order_id: int
    scope: str
    include_recommendations: bool
    context: dict[str, Any]
    etl: dict[str, Any]
    anomalies: list[dict[str, Any]]
    insights: dict[str, Any]
    response: dict[str, Any]


class InsightAnalysisEngine:
    def __init__(self, analytics_client: AnalyticsApiClient) -> None:
        self.analytics_client = analytics_client

        self.summary_prompt = PromptTemplate.from_template(
            "Create a grounded operations summary using only these structured analytics facts.\n"
            "ETL metrics: {etl}\n"
            "Detected anomalies: {anomalies}\n"
            "Key findings: {key_findings}\n"
            "Operational recommendations: {recommended_actions}"
        )
        self.order_prompt = PromptTemplate.from_template(
            "Explain the order delay using only structured analytics facts.\n"
            "ETL facts: {etl}\n"
            "Detected anomalies: {anomalies}\n"
            "Root causes: {root_causes}\n"
            "Operational recommendations: {recommended_actions}"
        )
        self.summary_chain = RunnableLambda(self._prepare_summary_prompt_payload) | RunnableLambda(
            self._render_summary_from_prompt_payload
        )
        self.order_chain = RunnableLambda(self._prepare_order_prompt_payload) | RunnableLambda(
            self._render_order_from_prompt_payload
        )

        graph = StateGraph(AnalysisState)
        graph.add_node("fetch_context", self._fetch_context)
        graph.add_node("run_etl", self._run_etl)
        graph.add_node("detect_anomalies", self._detect_anomalies)
        graph.add_node("derive_insights", self._derive_insights)
        graph.add_node("synthesize_summary", self._synthesize_summary)
        graph.add_edge(START, "fetch_context")
        graph.add_edge("fetch_context", "run_etl")
        graph.add_edge("run_etl", "detect_anomalies")
        graph.add_edge("detect_anomalies", "derive_insights")
        graph.add_edge("derive_insights", "synthesize_summary")
        graph.add_edge("synthesize_summary", END)
        self.graph = graph.compile()

    def analyze_order(self, order_id: int) -> OrderAnalysisResponse:
        result = self.graph.invoke({"mode": "order", "order_id": order_id})
        return OrderAnalysisResponse.model_validate(result["response"])

    def daily_summary(self, include_recommendations: bool) -> AIInsightResponse:
        result = self.graph.invoke(
            {"mode": "daily", "include_recommendations": include_recommendations}
        )
        return AIInsightResponse.model_validate(result["response"])

    def weekly_summary(self, include_recommendations: bool) -> AIInsightResponse:
        result = self.graph.invoke(
            {"mode": "weekly", "include_recommendations": include_recommendations}
        )
        return AIInsightResponse.model_validate(result["response"])

    def recommendations(self, scope: str) -> AIInsightResponse:
        result = self.graph.invoke({"mode": "recommendations", "scope": scope})
        return AIInsightResponse.model_validate(result["response"])

    def _fetch_context(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            context = {
                "order_report": self.analytics_client.get_order_report(state["order_id"]),
                "weekly_report": self.analytics_client.get_weekly_report(),
            }
            return {**state, "context": context}

        if mode == "daily":
            context = {
                "primary_report": self.analytics_client.get_daily_report(),
                "comparison_report": self.analytics_client.get_weekly_report(),
                "summary_report": self.analytics_client.get_summary_report(),
            }
            return {**state, "context": context}

        if mode == "weekly":
            context = {
                "primary_report": self.analytics_client.get_weekly_report(),
                "comparison_report": self.analytics_client.get_summary_report(),
            }
            return {**state, "context": context}

        if mode == "recommendations":
            scope = state.get("scope", "daily")
            if scope == "weekly":
                primary_report = self.analytics_client.get_weekly_report()
                comparison_report = self.analytics_client.get_summary_report()
            elif scope == "summary":
                primary_report = self.analytics_client.get_summary_report()
                comparison_report = None
            else:
                primary_report = self.analytics_client.get_daily_report()
                comparison_report = self.analytics_client.get_weekly_report()

            context = {
                "primary_report": primary_report,
                "comparison_report": comparison_report,
            }
            return {**state, "context": context}

        raise ValueError(f"Unsupported mode: {mode}")

    def _run_etl(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            etl = self._run_order_etl(state["context"])
        else:
            etl = self._run_report_etl(state["context"])
        return {**state, "etl": etl}

    def _detect_anomalies(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            anomalies = self._detect_order_anomalies(state["etl"])
        else:
            anomalies = self._detect_report_anomalies(state["etl"])
        return {**state, "anomalies": anomalies}

    def _derive_insights(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            insights = self._derive_order_insights(state["etl"], state["anomalies"])
        else:
            include_recommendations = mode == "recommendations" or bool(
                state.get("include_recommendations", True)
            )
            analysis_type = (
                f"{state.get('scope', 'daily')}-recommendations"
                if mode == "recommendations"
                else None
            )
            insights = self._derive_report_insights(
                state["etl"],
                state["anomalies"],
                include_recommendations=include_recommendations,
                analysis_type=analysis_type,
            )

        return {**state, "insights": insights}

    def _synthesize_summary(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            response = self.order_chain.invoke({"insights": state["insights"]})
        else:
            response = self.summary_chain.invoke({"insights": state["insights"]})
        return {**state, "response": response}

    def _run_report_etl(self, context: dict[str, Any]) -> dict[str, Any]:
        primary_report = context["primary_report"]
        comparison_report = context.get("comparison_report")
        metrics = primary_report.get("metrics", {})
        invoice_count_by_vendor = primary_report.get("invoice_count_by_vendor", [])
        bottleneck_distribution = primary_report.get("bottleneck_stage_distribution", [])
        top_vendor = invoice_count_by_vendor[0] if invoice_count_by_vendor else None
        bottleneck_stage = bottleneck_distribution[0]["stage"] if bottleneck_distribution else None
        cost_baseline, cost_baseline_label = self._derive_cost_baseline(
            primary_report, comparison_report
        )

        total_invoice_amount = metrics.get("total_invoice_amount")
        cost_spike_ratio = None
        if (
            total_invoice_amount is not None
            and cost_baseline is not None
            and cost_baseline > 0
        ):
            cost_spike_ratio = self._round_number(float(total_invoice_amount) / cost_baseline)

        return {
            "generatedAt": datetime.now().isoformat(timespec="seconds"),
            "reportType": primary_report.get("report_type", "summary"),
            "window": primary_report.get("window"),
            "metrics": metrics,
            "invoiceCountByVendor": invoice_count_by_vendor,
            "topVendor": top_vendor,
            "bottleneckStageDistribution": bottleneck_distribution,
            "mostCommonBottleneckStage": bottleneck_stage,
            "processingSample": primary_report.get("pandas_processing_sample"),
            "comparisonReportType": comparison_report.get("report_type")
            if comparison_report
            else None,
            "costBaseline": cost_baseline,
            "costBaselineLabel": cost_baseline_label,
            "costSpikeRatio": cost_spike_ratio,
        }

    def _run_order_etl(self, context: dict[str, Any]) -> dict[str, Any]:
        order_report = context["order_report"]
        weekly_report = context["weekly_report"]

        order = order_report.get("order", {})
        timeline = order_report.get("timeline", [])
        delay_analysis = order_report.get("delay_analysis", {})
        alerts = order_report.get("alerts", [])
        weekly_distribution = weekly_report.get("bottleneck_stage_distribution", [])
        weekly_bottleneck = weekly_distribution[0]["stage"] if weekly_distribution else None
        delay_stage = delay_analysis.get("primary_delay_stage")
        delay_stage_label = delay_analysis.get("primary_delay_stage_label") or self._humanize_stage(
            delay_stage
        )
        delay_stage_timeline = next(
            (item for item in timeline if item.get("milestone_type") == delay_stage),
            None,
        )
        days_late = self._days_difference(
            delay_analysis.get("expected_at"),
            delay_stage_timeline.get("actual_at") if delay_stage_timeline else None,
        )

        return {
            "generatedAt": datetime.now().isoformat(timespec="seconds"),
            "order": order,
            "timeline": timeline,
            "alerts": alerts,
            "delayAnalysis": delay_analysis,
            "delayStage": delay_stage,
            "delayStageLabel": delay_stage_label,
            "daysLate": days_late,
            "weeklyAnalytics": {
                "metrics": weekly_report.get("metrics", {}),
                "bottleneckStageDistribution": weekly_distribution,
            },
            "weeklyBottleneckStage": weekly_bottleneck,
        }

    def _detect_report_anomalies(self, etl: dict[str, Any]) -> list[dict[str, Any]]:
        anomalies: list[dict[str, Any]] = []
        metrics = etl["metrics"]
        bottleneck_stage = etl.get("mostCommonBottleneckStage")
        delayed_count = int(metrics.get("delayed_order_count", 0) or 0)
        at_risk_count = int(metrics.get("at_risk_order_count", 0) or 0)
        total_invoice_amount = metrics.get("total_invoice_amount", 0)

        if delayed_count > 0:
            anomaly_type = (
                "shipment_delay" if bottleneck_stage in SHIPMENT_RELATED_STAGES else "process_delay"
            )
            anomalies.append(
                {
                    "type": anomaly_type,
                    "severity": "high",
                    "message": (
                        f"{delayed_count} delayed order(s) are active, with the dominant bottleneck at "
                        f"{self._humanize_stage(bottleneck_stage)}."
                        if bottleneck_stage
                        else f"{delayed_count} delayed order(s) are active in the current analytics window."
                    ),
                    "evidence": {
                        "delayedOrderCount": delayed_count,
                        "bottleneckStage": bottleneck_stage,
                    },
                }
            )

        if at_risk_count > 0:
            anomalies.append(
                {
                    "type": "sla_risk",
                    "severity": "medium",
                    "message": f"{at_risk_count} order(s) are inside SLA warning windows.",
                    "evidence": {"atRiskOrderCount": at_risk_count},
                }
            )

        explicit_cost_spike = metrics.get("cost_spike_detected")
        cost_spike_ratio = etl.get("costSpikeRatio")
        if explicit_cost_spike or (
            cost_spike_ratio is not None and cost_spike_ratio >= COST_SPIKE_RATIO_THRESHOLD
        ):
            anomalies.append(
                {
                    "type": "cost_spike",
                    "severity": "high",
                    "message": (
                        f"Invoice amount appears elevated at {total_invoice_amount} versus the "
                        f"{etl.get('costBaselineLabel') or 'comparison baseline'} of {etl.get('costBaseline')}."
                    ),
                    "evidence": {
                        "totalInvoiceAmount": total_invoice_amount,
                        "costBaseline": etl.get("costBaseline"),
                        "costBaselineLabel": etl.get("costBaselineLabel"),
                        "costSpikeRatio": cost_spike_ratio,
                    },
                }
            )

        return anomalies

    def _detect_order_anomalies(self, etl: dict[str, Any]) -> list[dict[str, Any]]:
        anomalies: list[dict[str, Any]] = []
        delay_stage = etl.get("delayStage")
        delay_stage_label = etl.get("delayStageLabel")
        delay_analysis = etl.get("delayAnalysis", {})
        days_late = etl.get("daysLate")
        open_alert_count = int(etl.get("order", {}).get("open_alert_count", 0) or 0)
        weekly_bottleneck_stage = etl.get("weeklyBottleneckStage")

        if delay_stage:
            anomalies.append(
                {
                    "type": (
                        "shipment_delay"
                        if delay_stage in SHIPMENT_RELATED_STAGES
                        else "process_delay"
                    ),
                    "severity": (
                        "high"
                        if delay_analysis.get("primary_alert_type") == "SLA_BREACH"
                        else "medium"
                    ),
                    "message": f"{delay_stage_label} is the primary delayed stage for this order.",
                    "evidence": {
                        "delayStage": delay_stage,
                        "primaryAlertType": delay_analysis.get("primary_alert_type"),
                    },
                }
            )

        if days_late is not None and days_late > 0:
            anomalies.append(
                {
                    "type": "late_milestone",
                    "severity": "high",
                    "message": f"The order is running approximately {days_late} day(s) late.",
                    "evidence": {"daysLate": days_late},
                }
            )

        if delay_analysis.get("alert_message"):
            anomalies.append(
                {
                    "type": "alert_signal",
                    "severity": "medium",
                    "message": delay_analysis["alert_message"],
                    "evidence": {"alertMessage": delay_analysis["alert_message"]},
                }
            )

        if open_alert_count > 0:
            anomalies.append(
                {
                    "type": "open_alerts",
                    "severity": "medium",
                    "message": f"There are {open_alert_count} open analytics alert(s) on this order.",
                    "evidence": {"openAlertCount": open_alert_count},
                }
            )

        if weekly_bottleneck_stage and weekly_bottleneck_stage == delay_stage:
            anomalies.append(
                {
                    "type": "recurring_bottleneck",
                    "severity": "medium",
                    "message": (
                        f"{self._humanize_stage(delay_stage)} is also the dominant weekly bottleneck stage."
                    ),
                    "evidence": {"weeklyBottleneckStage": weekly_bottleneck_stage},
                }
            )

        return anomalies

    def _derive_report_insights(
        self,
        etl: dict[str, Any],
        anomalies: list[dict[str, Any]],
        include_recommendations: bool,
        analysis_type: str | None = None,
    ) -> dict[str, Any]:
        metrics = etl["metrics"]
        report_type = etl["reportType"]
        top_vendor = etl.get("topVendor")
        bottleneck_stage = etl.get("mostCommonBottleneckStage")

        key_findings = [
            f"Delayed orders: {metrics.get('delayed_order_count', 0)}",
            f"At-risk orders: {metrics.get('at_risk_order_count', 0)}",
            f"Total invoice amount in scope: {metrics.get('total_invoice_amount', 0)}",
        ]

        if metrics.get("average_production_duration_days") is not None:
            key_findings.append(
                f"Average production duration is {metrics['average_production_duration_days']} days."
            )
        if metrics.get("average_shipping_duration_days") is not None:
            key_findings.append(
                f"Average shipping duration is {metrics['average_shipping_duration_days']} days."
            )
        if metrics.get("average_customs_duration_days") is not None:
            key_findings.append(
                f"Average customs duration is {metrics['average_customs_duration_days']} days."
            )
        if top_vendor:
            key_findings.append(
                f"Top invoice volume vendor is {top_vendor['vendor']} with {top_vendor['invoice_count']} invoices in scope."
            )
        if metrics.get("on_time_delivery_rate_percent") is not None:
            key_findings.append(
                f"On-time delivery rate is {metrics['on_time_delivery_rate_percent']}%."
            )

        if etl.get("costSpikeRatio") is not None and etl["costSpikeRatio"] >= COST_SPIKE_RATIO_THRESHOLD:
            key_findings.append(
                f"Invoice amount is running at {etl['costSpikeRatio']}x the comparison baseline."
            )

        summary_facts = [
            f"The analytics window is reporting {metrics.get('delayed_order_count', 0)} delayed order(s) and {metrics.get('at_risk_order_count', 0)} at-risk order(s)."
        ]
        if bottleneck_stage:
            summary_facts.append(
                f"The dominant bottleneck stage is {self._humanize_stage(bottleneck_stage)}."
            )
        if anomalies:
            summary_facts.extend(anomaly["message"] for anomaly in anomalies[:3])

        recommendations = (
            self._build_report_recommendations(etl, anomalies)
            if include_recommendations
            else []
        )

        return {
            "analysisType": analysis_type or f"{report_type}-summary",
            "generatedAt": etl["generatedAt"],
            "reportType": report_type,
            "keyFindings": key_findings,
            "summaryFacts": summary_facts,
            "mostCommonBottleneckStage": bottleneck_stage,
            "recommendedActions": recommendations,
            "grounding": {
                "window": etl["window"],
                "etl": etl,
                "anomalies": anomalies,
            },
        }

    def _derive_order_insights(
        self, etl: dict[str, Any], anomalies: list[dict[str, Any]]
    ) -> dict[str, Any]:
        order = etl["order"]
        delay_stage = etl.get("delayStage")
        root_causes = [anomaly["message"] for anomaly in anomalies]

        if not root_causes:
            root_causes.append(
                "The order is not currently flagged as delayed by the analytics snapshot."
            )

        recommendations = self._build_order_recommendations(etl, anomalies)

        return {
            "analysisType": "order-delay-explanation",
            "generatedAt": etl["generatedAt"],
            "orderId": order["id"],
            "orderNumber": order["order_number"],
            "healthStatus": order["health_status"],
            "delayStage": delay_stage,
            "rootCauses": root_causes,
            "delayFacts": root_causes[:3],
            "recommendedActions": recommendations,
            "grounding": {
                "etl": etl,
                "anomalies": anomalies,
            },
        }

    def _build_report_recommendations(
        self, etl: dict[str, Any], anomalies: list[dict[str, Any]]
    ) -> list[str]:
        recommendations: list[str] = []
        metrics = etl["metrics"]
        bottleneck_stage = etl.get("mostCommonBottleneckStage")
        top_vendor = etl.get("topVendor")

        if bottleneck_stage:
            recommendations.extend(ACTION_PLAYBOOK.get(bottleneck_stage, []))

        if metrics.get("at_risk_order_count", 0) > 0:
            recommendations.append(
                "Review all at-risk orders in the next 24 hours and assign one owner per exception."
            )
        if metrics.get("delayed_order_count", 0) > 0:
            recommendations.append(
                "Run a daily delayed-order recovery stand-up focused on milestone owners and revised dates."
            )
        if top_vendor:
            recommendations.append(
                f"Validate invoice and logistics coordination with {top_vendor['vendor']} because it has the highest invoice volume in scope."
            )
        if any(anomaly["type"] == "cost_spike" for anomaly in anomalies):
            recommendations.append(
                "Review invoice amount drivers and confirm whether the current spend level reflects a one-time spike or a reporting anomaly."
            )

        return self._unique_preserving_order(recommendations)[:5]

    def _build_order_recommendations(
        self, etl: dict[str, Any], anomalies: list[dict[str, Any]]
    ) -> list[str]:
        recommendations: list[str] = []
        delay_stage = etl.get("delayStage")

        if delay_stage:
            recommendations.extend(ACTION_PLAYBOOK.get(delay_stage, []))

        if any(anomaly["type"] == "open_alerts" for anomaly in anomalies):
            recommendations.append(
                "Review the analytics alerts on this order and clear the oldest unresolved exception first."
            )
        recommendations.append(
            "Update the operations owner with a committed recovery date based on the breached stage evidence."
        )
        return self._unique_preserving_order(recommendations)[:5]

    def _prepare_summary_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        insights = payload["insights"]
        return {
            "prompt": self.summary_prompt.format(
                etl=insights["grounding"]["etl"],
                anomalies=insights["grounding"]["anomalies"],
                key_findings=insights["keyFindings"],
                recommended_actions=insights["recommendedActions"],
            ),
            "insights": insights,
        }

    def _render_summary_from_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        insights = payload["insights"]
        report_type = insights["reportType"]
        metrics = insights["grounding"]["etl"]["metrics"]
        is_recommendations = str(insights["analysisType"]).endswith("-recommendations")

        opening = (
            f"{report_type.capitalize()} operations recommendations: analytics shows {metrics.get('delayed_order_count', 0)} delayed order(s) "
            f"and {metrics.get('at_risk_order_count', 0)} at-risk order(s) in the current analytics window."
            if is_recommendations
            else f"{report_type.capitalize()} operations summary: analytics shows {metrics.get('delayed_order_count', 0)} delayed order(s) "
            f"and {metrics.get('at_risk_order_count', 0)} at-risk order(s) in the current analytics window."
        )

        narrative = " ".join([opening, *insights.get("summaryFacts", [])]).strip()

        return {
            "analysisType": insights["analysisType"],
            "generatedAt": insights["generatedAt"],
            "narrative": narrative,
            "keyFindings": insights["keyFindings"],
            "mostCommonBottleneckStage": insights["mostCommonBottleneckStage"],
            "recommendedActions": insights["recommendedActions"],
            "grounding": insights["grounding"],
        }

    def _prepare_order_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        insights = payload["insights"]
        return {
            "prompt": self.order_prompt.format(
                etl=insights["grounding"]["etl"],
                anomalies=insights["grounding"]["anomalies"],
                root_causes=insights["rootCauses"],
                recommended_actions=insights["recommendedActions"],
            ),
            "insights": insights,
        }

    def _render_order_from_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        insights = payload["insights"]
        order_number = insights["orderNumber"]
        health_status = insights["healthStatus"]
        delay_stage = insights.get("delayStage")

        if health_status == "DELAYED" and delay_stage:
            opening = (
                f"Order {order_number} is delayed at {self._humanize_stage(delay_stage)}."
            )
        else:
            opening = (
                f"Order {order_number} is not currently marked delayed by the analytics snapshot."
            )

        narrative = " ".join([opening, *insights.get("delayFacts", [])]).strip()

        return {
            "analysisType": insights["analysisType"],
            "generatedAt": insights["generatedAt"],
            "orderId": insights["orderId"],
            "orderNumber": order_number,
            "healthStatus": health_status,
            "delayStage": delay_stage,
            "narrative": narrative,
            "rootCauses": insights["rootCauses"],
            "recommendedActions": insights["recommendedActions"],
            "grounding": insights["grounding"],
        }

    def _derive_cost_baseline(
        self,
        primary_report: dict[str, Any],
        comparison_report: dict[str, Any] | None,
    ) -> tuple[float | None, str | None]:
        primary_metrics = primary_report.get("metrics", {})

        explicit_baseline = primary_metrics.get("cost_spike_baseline")
        if explicit_baseline is not None:
            return float(explicit_baseline), "report-provided cost baseline"

        if not comparison_report:
            return None, None

        primary_type = primary_report.get("report_type")
        comparison_type = comparison_report.get("report_type")
        comparison_total = comparison_report.get("metrics", {}).get("total_invoice_amount")
        if comparison_total is None:
            return None, None

        if primary_type == "daily" and comparison_type == "weekly":
            return self._round_number(float(comparison_total) / 7), "weekly daily-average invoice amount"

        return None, None

    @staticmethod
    def _days_difference(expected_at: str | None, actual_at: str | None) -> int | None:
        if not expected_at:
            return None

        expected_dt = datetime.fromisoformat(expected_at)
        comparison_dt = datetime.fromisoformat(actual_at) if actual_at else datetime.now()
        difference = comparison_dt - expected_dt
        if difference.total_seconds() <= 0:
            return 0
        return max(1, math.ceil(difference.total_seconds() / 86_400))

    @staticmethod
    def _humanize_stage(stage: str | None) -> str:
        if not stage:
            return "Unknown"
        return stage.replace("_", " ").title()

    @staticmethod
    def _round_number(value: float) -> float:
        return round(value, 2)

    @staticmethod
    def _unique_preserving_order(items: list[str]) -> list[str]:
        seen: set[str] = set()
        result: list[str] = []
        for item in items:
            if item not in seen:
                seen.add(item)
                result.append(item)
        return result
