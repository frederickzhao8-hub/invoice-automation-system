from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    host: str = "0.0.0.0"
    port: int = 8010
    tesseract_cmd: str | None = None
    ocr_languages: str = "eng+spa+chi_sim"
    frontend_origins: tuple[str, ...] = (
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://[::1]:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://[::1]:3000",
    )

    @classmethod
    def from_env(cls) -> "Settings":
        origins = tuple(
            value.strip()
            for value in os.getenv(
                "DELIVERY_IMAGE_FRONTEND_ORIGINS",
                (
                    "http://localhost:5173,"
                    "http://127.0.0.1:5173,"
                    "http://[::1]:5173,"
                    "http://localhost:3000,"
                    "http://127.0.0.1:3000,"
                    "http://[::1]:3000"
                ),
            ).split(",")
            if value.strip()
        )
        return cls(
            host=os.getenv("DELIVERY_IMAGE_HOST", "0.0.0.0"),
            port=int(os.getenv("DELIVERY_IMAGE_PORT", "8010")),
            tesseract_cmd=os.getenv("TESSERACT_CMD") or None,
            ocr_languages=os.getenv("DELIVERY_IMAGE_OCR_LANGS", "eng+spa+chi_sim"),
            frontend_origins=origins or ("http://localhost:5173",),
        )
