from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any

import pandas as pd
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine

MILESTONE_FLOW = [
    "PO_RECEIVED",
    "PRODUCTION_COMPLETED",
    "SHIPPED",
    "ARRIVED_PORT",
    "CUSTOMS_CLEARED",
    "DELIVERED",
]

MILESTONE_LABELS = {
    "PO_RECEIVED": "PO Received",
    "PRODUCTION_COMPLETED": "Production Completed",
    "SHIPPED": "Shipped",
    "ARRIVED_PORT": "Arrived at Port",
    "CUSTOMS_CLEARED": "Customs Cleared",
    "DELIVERED": "Delivered",
}


@dataclass(frozen=True)
class ReportWindow:
    name: str
    start: pd.Timestamp | None
    end: pd.Timestamp | None


class AnalyticsPipeline:
    def __init__(self, database_url: str) -> None:
        self.engine: Engine = create_engine(database_url, pool_pre_ping=True)

    def generate_daily_report(self) -> dict[str, Any]:
        end = pd.Timestamp.now(tz=None).floor("min")
        start = end - pd.Timedelta(days=1)
        return self._build_report(ReportWindow(name="daily", start=start, end=end))

    def generate_weekly_report(self) -> dict[str, Any]:
        end = pd.Timestamp.now(tz=None).floor("min")
        start = end - pd.Timedelta(days=7)
        return self._build_report(ReportWindow(name="weekly", start=start, end=end))

    def generate_summary_report(self) -> dict[str, Any]:
        return self._build_report(ReportWindow(name="summary", start=None, end=None))

    def generate_order_report(self, order_id: int) -> dict[str, Any]:
        frames = self._load_frames()
        orders = frames["orders"]
        milestones = frames["order_milestones"]
        alerts = frames["alerts"]

        order_rows = orders.loc[orders["id"] == order_id].copy()
        if order_rows.empty:
            raise ValueError(f"Order not found: {order_id}")

        order_row = order_rows.iloc[0]
        order_milestones = milestones.loc[milestones["order_id"] == order_id].copy()
        order_milestones = order_milestones.sort_values("occurred_at")
        order_alerts = alerts.loc[alerts["order_id"] == order_id].copy()
        if not order_alerts.empty:
            order_alerts = order_alerts.sort_values(["status", "triggered_at"], ascending=[True, False])

        actual_by_stage = {
            row["milestone_type"]: row
            for _, row in order_milestones.iterrows()
        }
        latest_alert_by_stage = {
            stage: stage_alerts.iloc[0]
            for stage, stage_alerts in order_alerts.groupby("milestone_type", sort=False)
        }

        current_stage = order_milestones.iloc[-1]["milestone_type"] if not order_milestones.empty else None
        current_index = MILESTONE_FLOW.index(current_stage) if current_stage in MILESTONE_FLOW else -1
        next_stage = (
            MILESTONE_FLOW[current_index + 1]
            if current_index >= 0 and current_index < len(MILESTONE_FLOW) - 1
            else None
        )

        open_breach_alerts = order_alerts.loc[
            (order_alerts["status"] == "OPEN") & (order_alerts["alert_type"] == "SLA_BREACH")
        ].copy()
        open_risk_alerts = order_alerts.loc[
            (order_alerts["status"] == "OPEN") & (order_alerts["alert_type"] == "AT_RISK")
        ].copy()

        if not open_breach_alerts.empty:
            primary_delay_alert = open_breach_alerts.sort_values(
                ["expected_at", "triggered_at"], ascending=[True, True]
            ).iloc[0]
            health_status = "DELAYED"
        elif not open_risk_alerts.empty:
            primary_delay_alert = open_risk_alerts.sort_values(
                ["expected_at", "triggered_at"], ascending=[True, True]
            ).iloc[0]
            health_status = "AT_RISK"
        else:
            primary_delay_alert = None
            health_status = "ON_TIME"

        timeline = [
            self._build_order_timeline_item(
                stage,
                actual_by_stage.get(stage),
                latest_alert_by_stage.get(stage),
            )
            for stage in MILESTONE_FLOW
        ]

        return {
            "report_type": "order",
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "order": {
                "id": int(order_row["id"]),
                "order_number": order_row["order_number"],
                "customer_name": order_row.get("customer_name"),
                "supplier_name": order_row.get("supplier_name"),
                "product_name": order_row.get("product_name"),
                "origin_country": order_row.get("origin_country"),
                "destination_country": order_row.get("destination_country"),
                "quantity": self._serialize_scalar(order_row.get("quantity")),
                "order_value": self._serialize_scalar(order_row.get("order_value")),
                "created_at": self._serialize_scalar(order_row.get("created_at")),
                "updated_at": self._serialize_scalar(order_row.get("updated_at")),
                "health_status": health_status,
                "current_stage": current_stage,
                "current_stage_label": self._stage_label(current_stage),
                "next_stage": next_stage,
                "next_stage_label": self._stage_label(next_stage),
                "open_alert_count": int(order_alerts.loc[order_alerts["status"] == "OPEN"].shape[0]),
            },
            "delay_analysis": {
                "primary_delay_stage": primary_delay_alert["milestone_type"] if primary_delay_alert is not None else None,
                "primary_delay_stage_label": self._stage_label(
                    primary_delay_alert["milestone_type"] if primary_delay_alert is not None else None
                ),
                "primary_alert_type": primary_delay_alert["alert_type"] if primary_delay_alert is not None else None,
                "expected_at": self._serialize_scalar(primary_delay_alert["expected_at"]) if primary_delay_alert is not None else None,
                "alert_message": primary_delay_alert["message"] if primary_delay_alert is not None else None,
                "delayed_stage_count": int(open_breach_alerts.shape[0]),
                "at_risk_stage_count": int(open_risk_alerts.shape[0]),
            },
            "timeline": timeline,
            "alerts": [self._build_order_alert_item(row) for _, row in order_alerts.iterrows()],
        }

    def _build_report(self, window: ReportWindow) -> dict[str, Any]:
        frames = self._load_frames()
        invoices = self._filter_window(frames["invoices"], "created_at", window)
        alerts = frames["alerts"]
        milestones = frames["order_milestones"]
        orders = frames["orders"]

        report = {
            "report_type": window.name,
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "window": {
                "start": window.start.isoformat() if window.start is not None else None,
                "end": window.end.isoformat() if window.end is not None else None,
            },
            "metrics": {
                "total_invoice_amount": self._round_number(invoices["amount"].fillna(0).sum())
                if "amount" in invoices.columns
                else 0.0,
                "average_production_duration_days": self._average_duration_days(
                    milestones, "PO_RECEIVED", "PRODUCTION_COMPLETED", window
                ),
                "average_shipping_duration_days": self._average_duration_days(
                    milestones, "PRODUCTION_COMPLETED", "SHIPPED", window
                ),
                "average_customs_duration_days": self._average_duration_days(
                    milestones, "ARRIVED_PORT", "CUSTOMS_CLEARED", window
                ),
                "delayed_order_count": self._current_order_count(alerts, "SLA_BREACH"),
                "at_risk_order_count": self._current_order_count(alerts, "AT_RISK"),
                "on_time_delivery_rate_percent": self._on_time_delivery_rate(
                    orders, milestones, alerts, window
                ),
            },
            "invoice_count_by_vendor": self._invoice_count_by_vendor(invoices),
            "bottleneck_stage_distribution": self._bottleneck_distribution(alerts, window),
            "pandas_processing_sample": self._pandas_processing_sample(invoices, milestones),
        }

        return report

    def _load_frames(self) -> dict[str, pd.DataFrame]:
        queries = {
            "invoices": "select * from invoices",
            "orders": "select * from orders",
            "order_milestones": "select * from order_milestones",
            "alerts": "select * from alerts",
        }
        date_columns = {
            "invoices": ["invoice_date", "due_date", "created_at", "updated_at"],
            "orders": ["created_at", "updated_at"],
            "order_milestones": ["occurred_at", "created_at", "updated_at"],
            "alerts": ["expected_at", "triggered_at", "resolved_at", "created_at", "updated_at"],
        }

        return {
            name: pd.read_sql_query(query, self.engine, parse_dates=date_columns[name])
            for name, query in queries.items()
        }

    def _filter_window(
        self, frame: pd.DataFrame, column_name: str, window: ReportWindow
    ) -> pd.DataFrame:
        if frame.empty or window.start is None or window.end is None or column_name not in frame.columns:
            return frame

        return frame.loc[(frame[column_name] >= window.start) & (frame[column_name] < window.end)].copy()

    def _average_duration_days(
        self,
        milestones: pd.DataFrame,
        start_stage: str,
        end_stage: str,
        window: ReportWindow,
    ) -> float | None:
        if milestones.empty:
            return None

        start_frame = (
            milestones.loc[milestones["milestone_type"] == start_stage, ["order_id", "occurred_at"]]
            .rename(columns={"occurred_at": "start_occurred_at"})
            .copy()
        )
        end_frame = (
            milestones.loc[milestones["milestone_type"] == end_stage, ["order_id", "occurred_at"]]
            .rename(columns={"occurred_at": "end_occurred_at"})
            .copy()
        )

        merged = end_frame.merge(start_frame, on="order_id", how="inner")
        if merged.empty:
            return None

        merged = merged.loc[merged["end_occurred_at"] >= merged["start_occurred_at"]].copy()
        if window.start is not None and window.end is not None:
            merged = merged.loc[
                (merged["end_occurred_at"] >= window.start) & (merged["end_occurred_at"] < window.end)
            ].copy()

        if merged.empty:
            return None

        merged["duration_days"] = (
            merged["end_occurred_at"] - merged["start_occurred_at"]
        ).dt.total_seconds() / 86_400

        return self._round_number(merged["duration_days"].mean())

    def _current_order_count(self, alerts: pd.DataFrame, alert_type: str) -> int:
        if alerts.empty:
            return 0

        filtered = alerts.loc[
            (alerts["status"] == "OPEN") & (alerts["alert_type"] == alert_type), "order_id"
        ]
        return int(filtered.nunique())

    def _on_time_delivery_rate(
        self,
        orders: pd.DataFrame,
        milestones: pd.DataFrame,
        alerts: pd.DataFrame,
        window: ReportWindow,
    ) -> float | None:
        if orders.empty or milestones.empty:
            return None

        delivered = milestones.loc[milestones["milestone_type"] == "DELIVERED", ["order_id", "occurred_at"]].copy()
        if window.start is not None and window.end is not None:
            delivered = delivered.loc[
                (delivered["occurred_at"] >= window.start) & (delivered["occurred_at"] < window.end)
            ].copy()

        if delivered.empty:
            return None

        breached_order_ids = set(
            alerts.loc[alerts["alert_type"] == "SLA_BREACH", "order_id"].dropna().astype(int).tolist()
        )
        delivered_order_ids = delivered["order_id"].dropna().astype(int)
        on_time_count = int(sum(order_id not in breached_order_ids for order_id in delivered_order_ids))

        return self._round_number((on_time_count / len(delivered_order_ids)) * 100)

    def _invoice_count_by_vendor(self, invoices: pd.DataFrame) -> list[dict[str, Any]]:
        if invoices.empty or "vendor" not in invoices.columns:
            return []

        grouped = (
            invoices.assign(vendor=invoices["vendor"].fillna("UNKNOWN"))
            .groupby("vendor", dropna=False)
            .size()
            .reset_index(name="invoice_count")
            .sort_values(["invoice_count", "vendor"], ascending=[False, True])
        )

        return grouped.to_dict(orient="records")

    def _bottleneck_distribution(
        self, alerts: pd.DataFrame, window: ReportWindow
    ) -> list[dict[str, Any]]:
        if alerts.empty:
            return []

        breach_alerts = alerts.loc[alerts["alert_type"] == "SLA_BREACH"].copy()
        if window.start is not None and window.end is not None:
            breach_alerts = breach_alerts.loc[
                (breach_alerts["triggered_at"] >= window.start)
                & (breach_alerts["triggered_at"] < window.end)
            ].copy()

        if breach_alerts.empty:
            return []

        counts = (
            breach_alerts.groupby("milestone_type")
            .size()
            .reset_index(name="count")
            .sort_values(["count", "milestone_type"], ascending=[False, True])
        )
        total = counts["count"].sum()
        counts["share_percent"] = counts["count"].apply(lambda value: self._round_number((value / total) * 100))
        counts = counts.rename(columns={"milestone_type": "stage"})
        return counts.to_dict(orient="records")

    def _pandas_processing_sample(
        self, invoices: pd.DataFrame, milestones: pd.DataFrame
    ) -> dict[str, Any]:
        if invoices.empty or milestones.empty or "amount" not in invoices.columns:
            return {
                "description": "No sample frame available yet.",
                "rows": [],
            }

        sample_invoice_totals = (
            invoices.assign(vendor=invoices["vendor"].fillna("UNKNOWN"))
            .groupby("vendor", dropna=False)["amount"]
            .sum()
            .reset_index(name="total_amount")
            .sort_values("total_amount", ascending=False)
            .head(5)
        )

        milestone_counts = (
            milestones.groupby("milestone_type")
            .size()
            .reset_index(name="milestone_count")
            .sort_values("milestone_count", ascending=False)
        )

        merged = sample_invoice_totals.assign(key=1).merge(
            milestone_counts.assign(key=1), on="key", how="left"
        )

        return {
            "description": "Sample Pandas processing that groups invoice totals by vendor and cross-joins "
            "them with milestone frequency data for downstream exploration.",
            "rows": merged.head(5).drop(columns="key").to_dict(orient="records"),
        }

    @staticmethod
    def _round_number(value: Any) -> float:
        return round(float(value), 2)

    def _build_order_timeline_item(
        self,
        stage: str,
        milestone_row: pd.Series | None,
        alert_row: pd.Series | None,
    ) -> dict[str, Any]:
        return {
            "milestone_type": stage,
            "milestone_label": self._stage_label(stage),
            "actual_at": self._serialize_scalar(milestone_row["occurred_at"]) if milestone_row is not None else None,
            "milestone_notes": milestone_row.get("notes") if milestone_row is not None else None,
            "alert_type": alert_row["alert_type"] if alert_row is not None else None,
            "alert_status": alert_row["status"] if alert_row is not None else None,
            "expected_at": self._serialize_scalar(alert_row["expected_at"]) if alert_row is not None else None,
            "alert_message": alert_row["message"] if alert_row is not None else None,
            "triggered_at": self._serialize_scalar(alert_row["triggered_at"]) if alert_row is not None else None,
        }

    def _build_order_alert_item(self, alert_row: pd.Series) -> dict[str, Any]:
        return {
            "id": int(alert_row["id"]),
            "milestone_type": alert_row["milestone_type"],
            "milestone_label": self._stage_label(alert_row["milestone_type"]),
            "alert_type": alert_row["alert_type"],
            "status": alert_row["status"],
            "severity": alert_row.get("severity"),
            "title": alert_row.get("title"),
            "message": alert_row.get("message"),
            "expected_at": self._serialize_scalar(alert_row.get("expected_at")),
            "triggered_at": self._serialize_scalar(alert_row.get("triggered_at")),
            "resolved_at": self._serialize_scalar(alert_row.get("resolved_at")),
        }

    @staticmethod
    def _stage_label(stage: str | None) -> str | None:
        if stage is None:
            return None
        return MILESTONE_LABELS.get(stage, stage.replace("_", " ").title())

    @staticmethod
    def _serialize_scalar(value: Any) -> Any:
        if pd.isna(value):
            return None
        if hasattr(value, "isoformat"):
            return value.isoformat()
        if isinstance(value, (int, str, bool)):
            return value
        try:
            return float(value)
        except (TypeError, ValueError):
            return value
