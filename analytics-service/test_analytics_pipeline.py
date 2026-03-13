from __future__ import annotations

import unittest

import pandas as pd

from analytics_pipeline import AnalyticsPipeline


class StubAnalyticsPipeline(AnalyticsPipeline):
    def __init__(self, frames: dict[str, pd.DataFrame]) -> None:
        self._frames = frames

    def _load_frames(self) -> dict[str, pd.DataFrame]:
        return self._frames


class AnalyticsPipelineTest(unittest.TestCase):
    def test_summary_report_metrics(self) -> None:
        pipeline = StubAnalyticsPipeline(
            {
                "invoices": pd.DataFrame(
                    [
                        {"vendor": "A1", "amount": 100.0, "created_at": pd.Timestamp("2026-03-01T10:00:00")},
                        {"vendor": "A1", "amount": 150.0, "created_at": pd.Timestamp("2026-03-02T10:00:00")},
                        {"vendor": "T1", "amount": 200.0, "created_at": pd.Timestamp("2026-03-03T10:00:00")},
                    ]
                ),
                "orders": pd.DataFrame(
                    [
                        {"id": 1, "order_number": "SC-1001"},
                        {"id": 2, "order_number": "SC-1002"},
                        {"id": 3, "order_number": "SC-1003"},
                    ]
                ),
                "order_milestones": pd.DataFrame(
                    [
                        {"order_id": 1, "milestone_type": "PO_RECEIVED", "occurred_at": pd.Timestamp("2026-01-01T09:00:00")},
                        {"order_id": 1, "milestone_type": "PRODUCTION_COMPLETED", "occurred_at": pd.Timestamp("2026-01-11T09:00:00")},
                        {"order_id": 1, "milestone_type": "SHIPPED", "occurred_at": pd.Timestamp("2026-01-15T09:00:00")},
                        {"order_id": 1, "milestone_type": "ARRIVED_PORT", "occurred_at": pd.Timestamp("2026-02-07T09:00:00")},
                        {"order_id": 1, "milestone_type": "CUSTOMS_CLEARED", "occurred_at": pd.Timestamp("2026-02-12T09:00:00")},
                        {"order_id": 1, "milestone_type": "DELIVERED", "occurred_at": pd.Timestamp("2026-02-13T09:00:00")},
                        {"order_id": 2, "milestone_type": "PO_RECEIVED", "occurred_at": pd.Timestamp("2026-01-05T09:00:00")},
                        {"order_id": 2, "milestone_type": "PRODUCTION_COMPLETED", "occurred_at": pd.Timestamp("2026-02-09T09:00:00")},
                        {"order_id": 3, "milestone_type": "PO_RECEIVED", "occurred_at": pd.Timestamp("2026-03-05T09:00:00")},
                    ]
                ),
                "alerts": pd.DataFrame(
                    [
                        {
                            "order_id": 2,
                            "alert_type": "SLA_BREACH",
                            "status": "OPEN",
                            "milestone_type": "PRODUCTION_COMPLETED",
                            "triggered_at": pd.Timestamp("2026-02-09T09:00:00"),
                        },
                        {
                            "order_id": 3,
                            "alert_type": "AT_RISK",
                            "status": "OPEN",
                            "milestone_type": "PRODUCTION_COMPLETED",
                            "triggered_at": pd.Timestamp("2026-03-09T09:00:00"),
                        },
                    ]
                ),
            }
        )

        report = pipeline.generate_summary_report()

        self.assertEqual(report["metrics"]["total_invoice_amount"], 450.0)
        self.assertEqual(report["metrics"]["average_production_duration_days"], 22.5)
        self.assertEqual(report["metrics"]["average_shipping_duration_days"], 4.0)
        self.assertEqual(report["metrics"]["average_customs_duration_days"], 5.0)
        self.assertEqual(report["metrics"]["delayed_order_count"], 1)
        self.assertEqual(report["metrics"]["at_risk_order_count"], 1)
        self.assertEqual(report["metrics"]["on_time_delivery_rate_percent"], 100.0)
        self.assertEqual(report["invoice_count_by_vendor"][0]["vendor"], "A1")
        self.assertEqual(report["invoice_count_by_vendor"][0]["invoice_count"], 2)
        self.assertEqual(report["bottleneck_stage_distribution"][0]["stage"], "PRODUCTION_COMPLETED")
        self.assertEqual(report["bottleneck_stage_distribution"][0]["count"], 1)

    def test_order_report_contains_delay_analysis_from_alerts(self) -> None:
        pipeline = StubAnalyticsPipeline(
            {
                "invoices": pd.DataFrame([]),
                "orders": pd.DataFrame(
                    [
                        {
                            "id": 2,
                            "order_number": "SC-1002",
                            "customer_name": "MetroNet",
                            "supplier_name": "FiberHome Wuhan",
                            "product_name": "ADSS Cable",
                            "origin_country": "China",
                            "destination_country": "Mexico",
                            "quantity": 10,
                            "order_value": 5000,
                            "created_at": pd.Timestamp("2026-03-01T10:00:00"),
                            "updated_at": pd.Timestamp("2026-03-09T10:00:00"),
                        }
                    ]
                ),
                "order_milestones": pd.DataFrame(
                    [
                        {"order_id": 2, "milestone_type": "PO_RECEIVED", "occurred_at": pd.Timestamp("2026-02-01T10:00:00"), "notes": "PO received"},
                        {"order_id": 2, "milestone_type": "PRODUCTION_COMPLETED", "occurred_at": pd.Timestamp("2026-03-05T10:00:00"), "notes": "Late factory completion"},
                    ]
                ),
                "alerts": pd.DataFrame(
                    [
                        {
                            "id": 11,
                            "order_id": 2,
                            "milestone_type": "PRODUCTION_COMPLETED",
                            "alert_type": "SLA_BREACH",
                            "status": "OPEN",
                            "severity": "CRITICAL",
                            "title": "Late production",
                            "message": "Production Completed breached SLA.",
                            "expected_at": pd.Timestamp("2026-03-02T10:00:00"),
                            "triggered_at": pd.Timestamp("2026-03-05T10:00:00"),
                            "resolved_at": pd.NaT,
                        }
                    ]
                ),
            }
        )

        report = pipeline.generate_order_report(2)

        self.assertEqual(report["order"]["health_status"], "DELAYED")
        self.assertEqual(report["delay_analysis"]["primary_delay_stage"], "PRODUCTION_COMPLETED")
        self.assertEqual(report["timeline"][1]["milestone_type"], "PRODUCTION_COMPLETED")
        self.assertEqual(report["timeline"][1]["alert_type"], "SLA_BREACH")


if __name__ == "__main__":
    unittest.main()
