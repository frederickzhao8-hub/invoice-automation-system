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


class AnalysisState(TypedDict, total=False):
    mode: str
    order_id: int
    scope: str
    include_recommendations: bool
    analytics_report: dict[str, Any]
    weekly_report: dict[str, Any]
    order_report: dict[str, Any]
    derived: dict[str, Any]
    response: dict[str, Any]


class InsightAnalysisEngine:
    def __init__(self, analytics_client: AnalyticsApiClient) -> None:
        self.analytics_client = analytics_client

        self.summary_prompt = PromptTemplate.from_template(
            "Create a grounded operations summary using only these structured analytics facts:\n"
            "{grounding}\n"
            "Most common bottleneck: {bottleneck_stage}\n"
            "Key findings: {key_findings}\n"
            "Recommended actions: {recommended_actions}"
        )
        self.order_prompt = PromptTemplate.from_template(
            "Explain the order delay using only structured analytics facts.\n"
            "Order analytics facts:\n{order_facts}\n"
            "Weekly analytics context:\n{analytics_facts}\n"
            "Root causes: {root_causes}\n"
            "Recommended actions: {recommended_actions}"
        )
        self.summary_chain = RunnableLambda(self._prepare_summary_prompt_payload) | RunnableLambda(
            self._render_summary_from_prompt_payload
        )
        self.order_chain = RunnableLambda(self._prepare_order_prompt_payload) | RunnableLambda(
            self._render_order_from_prompt_payload
        )

        graph = StateGraph(AnalysisState)
        graph.add_node("fetch_context", self._fetch_context)
        graph.add_node("derive_insights", self._derive_insights)
        graph.add_node("synthesize", self._synthesize)
        graph.add_edge(START, "fetch_context")
        graph.add_edge("fetch_context", "derive_insights")
        graph.add_edge("derive_insights", "synthesize")
        graph.add_edge("synthesize", END)
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
            return {
                **state,
                "order_report": self.analytics_client.get_order_report(state["order_id"]),
                "weekly_report": self.analytics_client.get_weekly_report(),
            }
        if mode == "daily":
            return {**state, "analytics_report": self.analytics_client.get_daily_report()}
        if mode == "weekly":
            return {**state, "analytics_report": self.analytics_client.get_weekly_report()}
        if mode == "recommendations":
            scope = state.get("scope", "daily")
            if scope == "weekly":
                report = self.analytics_client.get_weekly_report()
            elif scope == "summary":
                report = self.analytics_client.get_summary_report()
            else:
                report = self.analytics_client.get_daily_report()
            return {**state, "analytics_report": report}
        raise ValueError(f"Unsupported mode: {mode}")

    def _derive_insights(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        if mode == "order":
            derived = self._derive_order_insights(state["order_report"], state["weekly_report"])
        elif mode == "recommendations":
            scope = state.get("scope", "daily")
            derived = self._derive_report_insights(
                state["analytics_report"],
                include_recommendations=True,
                analysis_type=f"{scope}-recommendations",
            )
        else:
            derived = self._derive_report_insights(
                state["analytics_report"],
                include_recommendations=bool(state.get("include_recommendations", True)),
            )
        return {**state, "derived": derived}

    def _synthesize(self, state: AnalysisState) -> AnalysisState:
        mode = state["mode"]
        derived = state["derived"]
        if mode == "order":
            response = self.order_chain.invoke({"derived": derived})
        else:
            response = self.summary_chain.invoke({"derived": derived})
        return {**state, "response": response}

    def _derive_report_insights(
        self,
        report: dict[str, Any],
        include_recommendations: bool,
        analysis_type: str | None = None,
    ) -> dict[str, Any]:
        metrics = report.get("metrics", {})
        bottleneck_distribution = report.get("bottleneck_stage_distribution", [])
        invoice_count_by_vendor = report.get("invoice_count_by_vendor", [])
        bottleneck_stage = bottleneck_distribution[0]["stage"] if bottleneck_distribution else None

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
        if invoice_count_by_vendor:
            top_vendor = invoice_count_by_vendor[0]
            key_findings.append(
                f"Top invoice volume vendor is {top_vendor['vendor']} with "
                f"{top_vendor['invoice_count']} invoices in scope."
            )
        if metrics.get("on_time_delivery_rate_percent") is not None:
            key_findings.append(
                f"On-time delivery rate is {metrics['on_time_delivery_rate_percent']}%."
            )

        anomalies = []
        if metrics.get("delayed_order_count", 0) > 0:
            anomalies.append("There are active delayed orders requiring operational follow-up.")
        if metrics.get("at_risk_order_count", 0) > 0:
            anomalies.append("At-risk orders are inside their SLA warning windows.")
        if bottleneck_stage:
            anomalies.append(
                f"The most common bottleneck stage is {self._humanize_stage(bottleneck_stage)}."
            )

        recommendations = (
            self._build_recommendations(bottleneck_stage, metrics, invoice_count_by_vendor)
            if include_recommendations
            else []
        )

        return {
            "analysisType": analysis_type or f"{report['report_type']}-summary",
            "generatedAt": datetime.now().isoformat(timespec="seconds"),
            "reportType": report["report_type"],
            "metrics": metrics,
            "anomalies": anomalies,
            "keyFindings": key_findings,
            "mostCommonBottleneckStage": bottleneck_stage,
            "recommendedActions": recommendations,
            "grounding": {
                "window": report.get("window"),
                "metrics": metrics,
                "invoiceCountByVendor": invoice_count_by_vendor[:5],
                "bottleneckStageDistribution": bottleneck_distribution,
            },
        }

    def _derive_order_insights(
        self, order_report: dict[str, Any], weekly_report: dict[str, Any]
    ) -> dict[str, Any]:
        order = order_report.get("order", {})
        timeline = order_report.get("timeline", [])
        delay_analysis = order_report.get("delay_analysis", {})
        weekly_bottleneck = None
        bottleneck_distribution = weekly_report.get("bottleneck_stage_distribution", [])
        if bottleneck_distribution:
            weekly_bottleneck = bottleneck_distribution[0]["stage"]

        root_causes: list[str] = []
        delay_stage = delay_analysis.get("primary_delay_stage")
        delay_stage_label = delay_analysis.get("primary_delay_stage_label")
        delay_stage_timeline = next(
            (item for item in timeline if item.get("milestone_type") == delay_stage),
            None,
        )
        if delay_stage:
            if delay_analysis.get("primary_alert_type") == "SLA_BREACH":
                root_causes.append(
                    f"{delay_stage_label} is the first breached stage in the analytics snapshot."
                )
            else:
                root_causes.append(
                    f"{delay_stage_label} is the first stage currently flagged in the analytics snapshot."
                )

            days_late = self._days_difference(
                delay_analysis.get("expected_at"),
                delay_stage_timeline.get("actual_at") if delay_stage_timeline else None,
            )
            if days_late is not None and days_late > 0:
                root_causes.append(
                    f"The order is late by approximately {days_late} day(s) at "
                    f"{delay_stage_label}."
                )
            if delay_analysis.get("alert_message"):
                root_causes.append(delay_analysis["alert_message"])

        if order.get("open_alert_count", 0) > 0:
            root_causes.append(
                f"There are {order['open_alert_count']} open analytics alert(s) on the order."
            )
        if weekly_bottleneck and weekly_bottleneck == delay_stage:
            root_causes.append(
                f"{self._humanize_stage(delay_stage)} is also the most common bottleneck across the weekly analytics view."
            )
        if not root_causes:
            root_causes.append("The order is not currently flagged as delayed by the analytics snapshot.")

        recommendations = self._build_order_recommendations(delay_stage, order_report)

        return {
            "analysisType": "order-delay-explanation",
            "generatedAt": datetime.now().isoformat(timespec="seconds"),
            "orderId": order["id"],
            "orderNumber": order["order_number"],
            "healthStatus": order["health_status"],
            "delayStage": delay_stage,
            "rootCauses": root_causes,
            "recommendedActions": recommendations,
            "mostCommonBottleneckStage": weekly_bottleneck,
            "grounding": {
                "order": order,
                "delayAnalysis": delay_analysis,
                "alerts": order_report.get("alerts", []),
                "timeline": timeline,
                "weeklyAnalytics": {
                    "metrics": weekly_report.get("metrics", {}),
                    "bottleneckStageDistribution": bottleneck_distribution,
                },
            },
        }

    def _build_recommendations(
        self,
        bottleneck_stage: str | None,
        metrics: dict[str, Any],
        invoice_count_by_vendor: list[dict[str, Any]],
    ) -> list[str]:
        recommendations: list[str] = []

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
        if invoice_count_by_vendor:
            top_vendor = invoice_count_by_vendor[0]
            recommendations.append(
                f"Validate invoice and logistics coordination with {top_vendor['vendor']} because it has the highest invoice volume in scope."
            )

        return self._unique_preserving_order(recommendations)[:5]

    def _build_order_recommendations(
        self, delay_stage: str | None, order_report: dict[str, Any]
    ) -> list[str]:
        recommendations = []
        if delay_stage:
            recommendations.extend(ACTION_PLAYBOOK.get(delay_stage, []))

        if order_report.get("alerts"):
            recommendations.append(
                "Review the analytics alerts on this order and clear the oldest unresolved exception first."
            )
        recommendations.append(
            "Update the operations owner with a committed recovery date based on the breached stage evidence."
        )
        return self._unique_preserving_order(recommendations)[:5]

    def _prepare_summary_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        derived = payload["derived"]
        return {
            "prompt": self.summary_prompt.format(
                grounding=derived["grounding"],
                bottleneck_stage=derived.get("mostCommonBottleneckStage"),
                key_findings=derived["keyFindings"],
                recommended_actions=derived["recommendedActions"],
            ),
            "derived": derived,
        }

    def _render_summary_from_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        derived = payload["derived"]
        report_type = derived["reportType"]
        metrics = derived["metrics"]
        bottleneck_stage = derived.get("mostCommonBottleneckStage")
        is_recommendations = str(derived["analysisType"]).endswith("-recommendations")

        opening = (
            f"{report_type.capitalize()} operations recommendations: analytics shows {metrics.get('delayed_order_count', 0)} delayed order(s) "
            f"and {metrics.get('at_risk_order_count', 0)} at-risk order(s) in the current analytics window."
            if is_recommendations
            else f"{report_type.capitalize()} operations summary: there are {metrics.get('delayed_order_count', 0)} delayed order(s) "
            f"and {metrics.get('at_risk_order_count', 0)} at-risk order(s) in the current analytics window."
        )

        narrative_parts = [opening]

        if bottleneck_stage:
            narrative_parts.append(
                f"The most common bottleneck stage is {self._humanize_stage(bottleneck_stage)}."
            )
        if metrics.get("total_invoice_amount") is not None:
            narrative_parts.append(
                f"Total invoice amount in scope is {metrics['total_invoice_amount']}."
            )

        response = {
            "analysisType": derived["analysisType"],
            "generatedAt": derived["generatedAt"],
            "narrative": " ".join(narrative_parts),
            "keyFindings": derived["keyFindings"],
            "mostCommonBottleneckStage": bottleneck_stage,
            "recommendedActions": derived["recommendedActions"],
            "grounding": derived["grounding"],
        }
        return response

    def _prepare_order_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        derived = payload["derived"]
        return {
            "prompt": self.order_prompt.format(
                order_facts=derived["grounding"]["order"],
                analytics_facts=derived["grounding"]["weeklyAnalytics"],
                root_causes=derived["rootCauses"],
                recommended_actions=derived["recommendedActions"],
            ),
            "derived": derived,
        }

    def _render_order_from_prompt_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        derived = payload["derived"]
        order_number = derived["orderNumber"]
        health_status = derived["healthStatus"]
        delay_stage = derived.get("delayStage")

        if health_status == "DELAYED" and delay_stage:
            narrative = (
                f"Order {order_number} is delayed at {self._humanize_stage(delay_stage)}. "
                f"The delay explanation is grounded in the recorded milestone timeline, open alerts, and the weekly analytics bottleneck distribution."
            )
        else:
            narrative = (
                f"Order {order_number} is not currently marked delayed. The explanation is based on the latest order timeline and weekly analytics context."
            )

        return {
            "analysisType": derived["analysisType"],
            "generatedAt": derived["generatedAt"],
            "orderId": derived["orderId"],
            "orderNumber": order_number,
            "healthStatus": health_status,
            "delayStage": delay_stage,
            "narrative": narrative,
            "rootCauses": derived["rootCauses"],
            "recommendedActions": derived["recommendedActions"],
            "grounding": derived["grounding"],
        }

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
    def _unique_preserving_order(items: list[str]) -> list[str]:
        seen: set[str] = set()
        result: list[str] = []
        for item in items:
            if item not in seen:
                seen.add(item)
                result.append(item)
        return result
