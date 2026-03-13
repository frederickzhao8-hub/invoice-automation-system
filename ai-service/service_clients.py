from __future__ import annotations

from typing import Any

import httpx


class ServiceClientError(RuntimeError):
    pass


class MultiBaseUrlJsonClient:
    def __init__(self, base_urls: tuple[str, ...], timeout_seconds: float) -> None:
        self.base_urls = tuple(url.rstrip("/") for url in base_urls)
        self.timeout_seconds = timeout_seconds

    def get_json(self, path: str) -> dict[str, Any] | list[Any]:
        normalized_path = path if path.startswith("/") else f"/{path}"
        failures: list[str] = []

        for base_url in self.base_urls:
            url = f"{base_url}{normalized_path}"
            try:
                with httpx.Client(timeout=self.timeout_seconds) as client:
                    response = client.get(url)
                response.raise_for_status()
                return response.json()
            except Exception as exc:
                failures.append(f"{url}: {exc}")

        raise ServiceClientError(" | ".join(failures))


class AnalyticsApiClient:
    def __init__(self, base_urls: tuple[str, ...], timeout_seconds: float) -> None:
        self.client = MultiBaseUrlJsonClient(base_urls, timeout_seconds)

    def get_daily_report(self) -> dict[str, Any]:
        return self.client.get_json("/reports/daily")  # type: ignore[return-value]

    def get_weekly_report(self) -> dict[str, Any]:
        return self.client.get_json("/reports/weekly")  # type: ignore[return-value]

    def get_summary_report(self) -> dict[str, Any]:
        return self.client.get_json("/reports/summary")  # type: ignore[return-value]

    def get_order_report(self, order_id: int) -> dict[str, Any]:
        return self.client.get_json(f"/reports/orders/{order_id}")  # type: ignore[return-value]
