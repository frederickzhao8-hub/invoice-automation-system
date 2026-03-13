from __future__ import annotations

import os
from dataclasses import dataclass


def _split_csv(value: str | None, defaults: list[str]) -> tuple[str, ...]:
    if not value:
        return tuple(defaults)
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _default_frontend_origins(ports: tuple[int, ...]) -> list[str]:
    origins: list[str] = []
    for port in ports:
        origins.extend(
            [
                f"http://localhost:{port}",
                f"http://127.0.0.1:{port}",
                f"http://[::1]:{port}",
            ]
        )
    return origins


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    analytics_base_urls: tuple[str, ...]
    frontend_origins: tuple[str, ...]
    request_timeout_seconds: float

    @classmethod
    def from_env(cls) -> "Settings":
        analytics_defaults = [
            "http://127.0.0.1:5001",
            "http://localhost:5001",
            "http://[::1]:5001",
        ]

        analytics_single = os.getenv("AI_SERVICE_ANALYTICS_BASE_URL")

        analytics_urls = (
            (analytics_single.strip(),) if analytics_single and analytics_single.strip() else _split_csv(
                os.getenv("AI_SERVICE_ANALYTICS_BASE_URLS"),
                analytics_defaults,
            )
        )

        frontend_origins = _split_csv(
            os.getenv("AI_SERVICE_FRONTEND_ORIGINS"),
            _default_frontend_origins((5173, 3000, 3001, 3002, 3003)),
        )

        return cls(
            host=os.getenv("AI_SERVICE_HOST", "127.0.0.1"),
            port=int(os.getenv("AI_SERVICE_PORT", "8001")),
            analytics_base_urls=analytics_urls,
            frontend_origins=frontend_origins,
            request_timeout_seconds=float(os.getenv("AI_SERVICE_REQUEST_TIMEOUT_SECONDS", "10")),
        )
