from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd


class ReportWriter:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir

    def report_to_csv(self, report: dict[str, Any]) -> str:
        rows: list[dict[str, Any]] = [
            {
                "section": "metadata",
                "label": "report_type",
                "dimension": "",
                "value": report["report_type"],
            },
            {
                "section": "metadata",
                "label": "generated_at",
                "dimension": "",
                "value": report["generated_at"],
            },
            {
                "section": "metadata",
                "label": "window_start",
                "dimension": "",
                "value": report["window"]["start"],
            },
            {
                "section": "metadata",
                "label": "window_end",
                "dimension": "",
                "value": report["window"]["end"],
            },
        ]

        rows.extend(
            {
                "section": "metrics",
                "label": metric_name,
                "dimension": "",
                "value": metric_value,
            }
            for metric_name, metric_value in report["metrics"].items()
        )

        rows.extend(
            {
                "section": "invoice_count_by_vendor",
                "label": "invoice_count",
                "dimension": row["vendor"],
                "value": row["invoice_count"],
            }
            for row in report["invoice_count_by_vendor"]
        )

        rows.extend(
            {
                "section": "bottleneck_stage_distribution",
                "label": "breach_count",
                "dimension": row["stage"],
                "value": row["count"],
            }
            for row in report["bottleneck_stage_distribution"]
        )

        rows.extend(
            {
                "section": "bottleneck_stage_distribution",
                "label": "breach_share_percent",
                "dimension": row["stage"],
                "value": row["share_percent"],
            }
            for row in report["bottleneck_stage_distribution"]
        )

        return pd.DataFrame(rows).to_csv(index=False)

    def persist_report(self, report: dict[str, Any]) -> dict[str, Path]:
        report_type = report["report_type"]
        generated_date = report["generated_at"][:10]
        report_dir = self.output_dir / report_type
        report_dir.mkdir(parents=True, exist_ok=True)

        json_path = report_dir / f"{report_type}-{generated_date}.json"
        csv_path = report_dir / f"{report_type}-{generated_date}.csv"

        json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
        csv_path.write_text(self.report_to_csv(report), encoding="utf-8")

        return {"json": json_path, "csv": csv_path}
