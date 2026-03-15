from __future__ import annotations

from io import BytesIO
from pathlib import Path
from typing import Protocol


PRIMARY_OCR_PASSES = (
    ("line_removed", "--oem 3 --psm 6"),
    ("line_removed", "--oem 3 --psm 4"),
    ("enhanced", "--oem 3 --psm 4"),
)
FALLBACK_OCR_PASSES = (
    ("line_removed", "--oem 3 --psm 11"),
    ("thresholded", "--oem 3 --psm 4"),
    ("thresholded", "--oem 3 --psm 6"),
    ("enhanced", "--oem 3 --psm 6"),
    ("enhanced", "--oem 3 --psm 11"),
)
OCR_HINT_TOKENS = (
    "ubicacion",
    "ubicación",
    "fecha",
    "orden de compra",
    "nota de entrada",
    "item",
    "articulo",
    "artículo",
    "cantidad",
    "po",
    "entry note",
)


class OcrProvider(Protocol):
    def extract_text(self, image_bytes: bytes) -> str:
        ...


class TesseractOcrProvider:
    def __init__(self, lang: str = "eng+spa+chi_sim", tesseract_cmd: str | None = None) -> None:
        self.lang = lang
        self.tesseract_cmd = tesseract_cmd

    def extract_text(self, image_bytes: bytes) -> str:
        try:
            from PIL import Image, ImageEnhance, ImageFilter, ImageOps
            import pytesseract
        except ImportError as exc:  # pragma: no cover - runtime dependency guard
            raise RuntimeError(
                "OCR dependencies are not installed. Install Pillow and pytesseract first."
            ) from exc

        if self.tesseract_cmd:
            pytesseract.pytesseract.tesseract_cmd = self.tesseract_cmd

        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except Exception as exc:  # pragma: no cover - image decoding guard
            raise ValueError("Unable to read the uploaded image.") from exc

        prepared_images = prepare_ocr_images(image, Image, ImageOps, ImageEnhance, ImageFilter)

        best_text = ""
        best_score = (-1, -1, -1)

        for image_name, ocr_config in PRIMARY_OCR_PASSES:
            extracted_text = pytesseract.image_to_string(
                prepared_images[image_name],
                lang=self.lang,
                config=ocr_config,
            )
            extracted_score = _ocr_text_score(extracted_text)
            if extracted_score > best_score:
                best_text = extracted_text
                best_score = extracted_score

            if _is_high_confidence_text(extracted_score):
                return normalize_ocr_text(extracted_text)

        for image_name, ocr_config in FALLBACK_OCR_PASSES:
            extracted_text = pytesseract.image_to_string(
                prepared_images[image_name],
                lang=self.lang,
                config=ocr_config,
            )
            extracted_score = _ocr_text_score(extracted_text)
            if extracted_score > best_score:
                best_text = extracted_text
                best_score = extracted_score

        return normalize_ocr_text(best_text)


def prepare_ocr_images(image, image_module, image_ops_module, image_enhance_module, image_filter_module):
    grayscale = image_ops_module.grayscale(image)
    autocontrasted = image_ops_module.autocontrast(grayscale)

    upscaled = autocontrasted.resize(
        (autocontrasted.width * 2, autocontrasted.height * 2),
        image_module.Resampling.LANCZOS,
    )
    sharpened = upscaled.filter(image_filter_module.SHARPEN)
    enhanced = image_enhance_module.Contrast(sharpened).enhance(1.6)

    thresholded = enhanced.point(lambda pixel: 255 if pixel > 180 else 0)
    line_removed = remove_table_lines(thresholded)
    return {
        "enhanced": enhanced,
        "thresholded": thresholded,
        "line_removed": line_removed,
    }


def remove_table_lines(image):
    cleaned = image.copy()
    pixels = cleaned.load()
    width, height = cleaned.size

    horizontal_lines = [
        y
        for y in range(height)
        if sum(1 for x in range(width) if pixels[x, y] == 0) > width * 0.6
    ]
    vertical_lines = [
        x
        for x in range(width)
        if sum(1 for y in range(height) if pixels[x, y] == 0) > height * 0.4
    ]

    _erase_detected_lines(pixels, width, height, horizontal_lines, vertical_lines)
    return cleaned


def _erase_detected_lines(pixels, width: int, height: int, rows: list[int], columns: list[int]) -> None:
    for row in rows:
        for offset in range(-2, 3):
            current_row = row + offset
            if 0 <= current_row < height:
                for x in range(width):
                    pixels[x, current_row] = 255

    for column in columns:
        for offset in range(-2, 3):
            current_column = column + offset
            if 0 <= current_column < width:
                for y in range(height):
                    pixels[current_column, y] = 255


def _ocr_text_score(value: str) -> tuple[int, int, int]:
    normalized_value = normalize_ocr_text(value)
    line_count = len([line for line in normalized_value.splitlines() if line.strip()])
    alpha_count = sum(char.isalpha() for char in normalized_value)
    hint_score = sum(token in normalized_value.lower() for token in OCR_HINT_TOKENS)
    return (hint_score, line_count, alpha_count)


def _is_high_confidence_text(score: tuple[int, int, int]) -> bool:
    hint_score, line_count, alpha_count = score
    return hint_score >= 4 and line_count >= 4 and alpha_count >= 30


def normalize_ocr_text(value: str) -> str:
    return (
        value.replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\u0000", "")
        .strip()
    )


def is_supported_image(file_name: str | None, content_type: str | None) -> bool:
    normalized_name = (file_name or "").lower()
    normalized_content_type = (content_type or "").lower()
    return (
        normalized_name.endswith((".jpg", ".jpeg", ".png"))
        or normalized_content_type in {"image/jpeg", "image/jpg", "image/png"}
    )


def file_suffix(file_name: str | None) -> str:
    return Path(file_name or "").suffix.lower()
