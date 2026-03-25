from __future__ import annotations

import unittest

from analysis_engine import InsightAnalysisEngine


class StubAnalyticsClient:
    def get_daily_report(self) -> dict:
        return {
            "report_type": "daily",
            "metrics": {
                "total_invoice_amount": 1500.0,
                "delayed_order_count": 2,
                "at_risk_order_count": 1,
                "average_production_duration_days": 18.0,
                "average_shipping_duration_days": 3.0,
                "average_customs_duration_days": None,
                "on_time_delivery_rate_percent": 82.0,
            },
            "invoice_count_by_vendor": [{"vendor": "A1", "invoice_count": 4}],
            "bottleneck_stage_distribution": [{"stage": "SHIPPED", "count": 2, "share_percent": 100.0}],
            "window": {"start": "2026-03-08T00:00:00", "end": "2026-03-09T00:00:00"},
        }

    def get_weekly_report(self) -> dict:
        return {
            "report_type": "weekly",
            "metrics": {
                "total_invoice_amount": 4500.0,
                "delayed_order_count": 3,
                "at_risk_order_count": 1,
                "average_production_duration_days": 20.0,
                "average_shipping_duration_days": 4.0,
                "average_customs_duration_days": 5.0,
                "on_time_delivery_rate_percent": 90.0,
            },
            "invoice_count_by_vendor": [{"vendor": "T1", "invoice_count": 7}],
            "bottleneck_stage_distribution": [
                {"stage": "PRODUCTION_COMPLETED", "count": 2, "share_percent": 66.67},
                {"stage": "SHIPPED", "count": 1, "share_percent": 33.33},
            ],
            "window": {"start": "2026-03-02T00:00:00", "end": "2026-03-09T00:00:00"},
        }

    def get_summary_report(self) -> dict:
        return self.get_weekly_report() | {"report_type": "summary"}

    def get_order_report(self, order_id: int) -> dict:
        return {
            "order": {
                "id": order_id,
                "order_number": "SC-1002",
                "health_status": "DELAYED",
                "current_stage": "PRODUCTION_COMPLETED",
                "current_stage_label": "Production Completed",
                "next_stage": "SHIPPED",
                "next_stage_label": "Shipped",
                "open_alert_count": 2,
            },
            "delay_analysis": {
                "primary_delay_stage": "PRODUCTION_COMPLETED",
                "primary_delay_stage_label": "Production Completed",
                "primary_alert_type": "SLA_BREACH",
                "expected_at": "2026-03-02T10:00:00",
                "alert_message": "Production Completed breached SLA.",
            },
            "alerts": [{"title": "SLA breach for Shipped"}],
            "timeline": [
                {
                    "milestone_type": "PO_RECEIVED",
                    "milestone_label": "PO Received",
                    "expected_at": None,
                    "actual_at": "2026-02-01T10:00:00",
                },
                {
                    "milestone_type": "PRODUCTION_COMPLETED",
                    "milestone_label": "Production Completed",
                    "expected_at": "2026-03-02T10:00:00",
                    "actual_at": "2026-03-05T10:00:00",
                    "alert_type": "SLA_BREACH",
                },
                {
                    "milestone_type": "SHIPPED",
                    "milestone_label": "Shipped",
                    "expected_at": "2026-03-08T10:00:00",
                    "actual_at": None,
                    "alert_type": "SLA_BREACH",
                },
            ],
        }


class InsightAnalysisEngineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = InsightAnalysisEngine(StubAnalyticsClient())

    def test_daily_summary_contains_bottleneck_and_recommendations(self) -> None:
        response = self.engine.daily_summary(True)
        self.assertEqual(response.analysis_type, "daily-summary")
        self.assertEqual(response.most_common_bottleneck_stage, "SHIPPED")
        self.assertTrue(response.recommended_actions)
        self.assertIn("etl", response.grounding)
        self.assertIn("anomalies", response.grounding)
        self.assertTrue(
            any(item["type"] == "cost_spike" for item in response.grounding["anomalies"])
        )

    def test_order_analysis_uses_order_delay_stage(self) -> None:
        response = self.engine.analyze_order(2)
        self.assertEqual(response.order_id, 2)
        self.assertEqual(response.delay_stage, "PRODUCTION_COMPLETED")
        self.assertTrue(
            any("breached" in cause.lower() or "late" in cause.lower() for cause in response.root_causes)
        )
        self.assertTrue(any("3 day(s)" in cause for cause in response.root_causes))
        self.assertIn("etl", response.grounding)
        self.assertIn("anomalies", response.grounding)

    def test_recommendations_response_uses_recommendations_analysis_type(self) -> None:
        response = self.engine.recommendations("weekly")
        self.assertEqual(response.analysis_type, "weekly-recommendations")
        self.assertTrue(response.narrative.startswith("Weekly operations recommendations"))


if __name__ == "__main__":
    unittest.main()
