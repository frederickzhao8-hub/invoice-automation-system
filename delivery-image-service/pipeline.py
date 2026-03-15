from __future__ import annotations

from dataclasses import dataclass

from models import DeliveryImageExtractionResponse
from ocr_provider import OcrProvider, is_supported_image
from text_parser import parse_delivery_ocr_text


@dataclass
class DeliveryImageExtractionPipeline:
    ocr_provider: OcrProvider

    def process_image(
        self,
        file_name: str | None,
        content_type: str | None,
        image_bytes: bytes,
    ) -> DeliveryImageExtractionResponse:
        if not image_bytes:
            raise ValueError("Uploaded image is empty.")

        if not is_supported_image(file_name, content_type):
            raise ValueError("Only jpg, jpeg, and png images are supported.")

        raw_text = self.ocr_provider.extract_text(image_bytes)
        return parse_delivery_ocr_text(raw_text)
