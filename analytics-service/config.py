from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _to_sqlalchemy_url(jdbc_url: str, username: str, password: str) -> str:
    normalized = jdbc_url.removeprefix("jdbc:")
    if not normalized.startswith("postgresql://"):
        raise ValueError(
            "SPRING_DATASOURCE_URL must be a PostgreSQL JDBC URL, for example "
            "'jdbc:postgresql://localhost:5432/invoice_automation'."
        )

    return normalized.replace("postgresql://", f"postgresql+psycopg://{username}:{password}@", 1)


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
    database_url: str
    report_output_dir: Path
    host: str
    port: int
    schedule_hour: int
    schedule_minute: int
    timezone: str
    cors_allowed_origins: tuple[str, ...]

    @classmethod
    def from_env(cls, base_dir: Path) -> "Settings":
        spring_url = os.getenv(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/invoice_automation"
        )
        spring_username = os.getenv("SPRING_DATASOURCE_USERNAME", "postgres")
        spring_password = os.getenv("SPRING_DATASOURCE_PASSWORD", "postgres")

        database_url = os.getenv(
            "ANALYTICS_DATABASE_URL",
            _to_sqlalchemy_url(spring_url, spring_username, spring_password),
        )

        report_output_dir = Path(
            os.getenv("ANALYTICS_REPORT_OUTPUT_DIR", base_dir / "generated-reports")
        ).expanduser()

        return cls(
            database_url=database_url,
            report_output_dir=report_output_dir,
            host=os.getenv("ANALYTICS_SERVICE_HOST", "127.0.0.1"),
            port=int(os.getenv("ANALYTICS_SERVICE_PORT", "5001")),
            schedule_hour=int(os.getenv("ANALYTICS_SCHEDULE_HOUR", "6")),
            schedule_minute=int(os.getenv("ANALYTICS_SCHEDULE_MINUTE", "0")),
            timezone=os.getenv("ANALYTICS_SERVICE_TIMEZONE", "America/Los_Angeles"),
            cors_allowed_origins=_split_csv(
                os.getenv("ANALYTICS_CORS_ALLOWED_ORIGINS"),
                _default_frontend_origins((5173, 3000, 3001, 3002, 3003)),
            ),
        )
