from __future__ import annotations

import atexit
import logging
import os
from pathlib import Path
from typing import Any, Callable

from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask, Response, jsonify, request
from sqlalchemy.exc import SQLAlchemyError

from analytics_pipeline import AnalyticsPipeline
from config import Settings
from report_writer import ReportWriter


BASE_DIR = Path(__file__).resolve().parent
SETTINGS = Settings.from_env(BASE_DIR)
PIPELINE = AnalyticsPipeline(SETTINGS.database_url)
WRITER = ReportWriter(SETTINGS.report_output_dir)


def create_app() -> Flask:
    app = Flask(__name__)
    app.logger.setLevel(logging.INFO)

    @app.before_request
    def handle_preflight() -> Response | None:
        if request.method == "OPTIONS":
            return Response(status=204)
        return None

    @app.after_request
    def add_cors_headers(response: Response) -> Response:
        origin = request.headers.get("Origin")
        if origin and origin in SETTINGS.cors_allowed_origins:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
            response.headers["Access-Control-Allow-Methods"] = "GET, OPTIONS"
            response.headers["Vary"] = "Origin"
        return response

    @app.get("/health")
    def health() -> Response:
        return jsonify({"status": "ok"})

    @app.get("/reports/daily")
    def daily_report() -> Response:
        return _report_response(app, "daily", PIPELINE.generate_daily_report)

    @app.get("/reports/weekly")
    def weekly_report() -> Response:
        return _report_response(app, "weekly", PIPELINE.generate_weekly_report)

    @app.get("/reports/summary")
    def summary_report() -> Response:
        return _report_response(app, "summary", PIPELINE.generate_summary_report)

    @app.get("/reports/orders/<int:order_id>")
    def order_report(order_id: int) -> Response:
        return _report_response(app, f"order-{order_id}", lambda: PIPELINE.generate_order_report(order_id))

    return app


def _report_response(
    app: Flask,
    report_name: str,
    builder: Callable[[], dict[str, Any]],
) -> Response:
    try:
        report = builder()
    except ValueError as exc:
        return jsonify({"message": str(exc)}), 404
    except SQLAlchemyError as exc:
        app.logger.exception("Database error while generating %s report", report_name)
        return jsonify({"message": f"Unable to query PostgreSQL for the {report_name} report.", "detail": str(exc)}), 500
    except Exception as exc:  # pragma: no cover - defensive runtime guard
        app.logger.exception("Unexpected error while generating %s report", report_name)
        return jsonify({"message": f"Unable to generate the {report_name} report.", "detail": str(exc)}), 500

    output_format = request.args.get("format", "json").strip().lower()
    should_persist = request.args.get("persist", "false").strip().lower() == "true"

    if should_persist:
        WRITER.persist_report(report)

    if output_format == "csv":
        csv_body = WRITER.report_to_csv(report)
        return Response(
            csv_body,
            mimetype="text/csv",
            headers={
                "Content-Disposition": f'attachment; filename="{report_name}-report.csv"'
            },
        )

    return jsonify(report)


def _generate_scheduled_daily_report(app: Flask) -> None:
    with app.app_context():
        report = PIPELINE.generate_daily_report()
        paths = WRITER.persist_report(report)
        app.logger.info(
            "Generated scheduled daily analytics report: json=%s csv=%s",
            paths["json"],
            paths["csv"],
        )


def _ensure_initial_daily_report(app: Flask) -> None:
    try:
        _generate_scheduled_daily_report(app)
    except Exception:  # pragma: no cover - startup logging path
        app.logger.exception("Unable to generate the initial daily analytics report.")


app = create_app()
scheduler = BackgroundScheduler(timezone=SETTINGS.timezone)
scheduler.add_job(
    _generate_scheduled_daily_report,
    "cron",
    hour=SETTINGS.schedule_hour,
    minute=SETTINGS.schedule_minute,
    args=[app],
    id="daily-analytics-report",
    replace_existing=True,
)
scheduler.start()
atexit.register(lambda: scheduler.shutdown(wait=False))


if __name__ == "__main__":
    if os.getenv("ANALYTICS_GENERATE_ON_STARTUP", "false").strip().lower() == "true":
        _ensure_initial_daily_report(app)
    app.run(host=SETTINGS.host, port=SETTINGS.port, debug=False)
