from __future__ import annotations

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str


class DeliveryImageExtractionResponse(BaseModel):
    item_name: str | None
    quantity: int | None
    date: str | None
    location: str | None
    po_number: str | None
    entry_note: str | None
    raw_text: str
