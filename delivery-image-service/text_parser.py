from __future__ import annotations

import re
from dataclasses import dataclass

from models import DeliveryImageExtractionResponse


FIELD_PREFIX_PATTERN = re.compile(
    r"(?i)^\s*(?:"
    r"location|ubicaci[oó]n|warehouse|bodega|almac[eé]n|almacen|"
    r"po|purchase\s*order|orden\s*de\s*compra|"
    r"entry\s*note|delivery\s*note|nota\s*de\s*entrada|nota\s*de\s*entrega|"
    r"item|material|product|sku|art[ií]culo|producto|品名|物料|"
    r"quantity|qty|cantidad|units?|pieces?|pcs|数量|"
    r"date|fecha"
    r")\b"
)

PO_PATTERNS = [
    re.compile(r"(?i)\bPO(?:\s*No\.?)?\b\s*[:#-]?\s*([A-Z0-9-]{4,})"),
    re.compile(r"(?i)\bPurchase\s*Order\b\s*[:#-]?\s*([A-Z0-9-]{4,})"),
    re.compile(r"(?i)\bOrden\s*de\s*compra\b\s*[:#-]?\s*([A-Z0-9-]{4,})"),
]

ENTRY_NOTE_PATTERNS = [
    re.compile(r"(?i)\bEntry\s*Note\b\s*[:#-]?\s*([A-Z0-9-]{2,})"),
    re.compile(r"(?i)\bDelivery\s*Note\b\s*[:#-]?\s*([A-Z0-9-]{2,})"),
    re.compile(r"(?i)\bNota\s*de\s*entrada\b\s*[:#-]?\s*([A-Z0-9-]{2,})"),
    re.compile(r"(?i)\bNota\s*de\s*entrega\b\s*[:#-]?\s*([A-Z0-9-]{2,})"),
    re.compile(r"(?i)\bEN\b\s*[:#-]?\s*([A-Z0-9-]{2,})"),
]

ITEM_PATTERNS = [
    re.compile(r"(?i)\bItem\b\s*[:#-]?\s*([^\n]+)"),
    re.compile(r"(?i)\bMaterial\b\s*[:#-]?\s*([^\n]+)"),
    re.compile(r"(?i)\bProduct\b\s*[:#-]?\s*([^\n]+)"),
    re.compile(r"(?i)\bArt[ií]culo\b\s*[:#-]?\s*([^\n]+)"),
    re.compile(r"(?i)\bProducto\b\s*[:#-]?\s*([^\n]+)"),
    re.compile(r"(?i)(?:品名|物料)\s*[:#：-]?\s*([^\n]+)"),
]

QUANTITY_PATTERNS = [
    re.compile(r"(?i)\b(?:Quantity|Qty|Cantidad|Units?|Pieces?|PCS|数量)\b\s*[:#-]?\s*([0-9][0-9,]*)"),
]

DATE_PATTERNS = [
    re.compile(r"(?<!\d)(\d{4}[-/]\d{1,2}[-/]\d{1,2})(?!\d)"),
    re.compile(r"(?<!\d)(\d{1,2}\.\d{1,2})(?!\d)"),
    re.compile(r"(?<!\d)(\d{1,2}[-/]\d{1,2})(?!\d)"),
]

LOCATION_LABEL_PATTERNS = [
    re.compile(r"(?i)^ubicaci[oó]n\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^location\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^warehouse\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^bodega\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^almac[eé]n\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^almacen\b[:#-]?\s*(.*)$"),
]

PO_LABEL_PATTERNS = [
    re.compile(r"(?i)^po(?:\s*no\.?)?\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^purchase\s*order\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^orden\s*de\s*compra\b[:#-]?\s*(.*)$"),
]

ENTRY_NOTE_LABEL_PATTERNS = [
    re.compile(r"(?i)^entry\s*note\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^delivery\s*note\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^nota\s*de\s*entrada\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^nota\s*de\s*entrega\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^en\b[:#-]?\s*(.*)$"),
]

ITEM_LABEL_PATTERNS = [
    re.compile(r"(?i)^item\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^material\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^product\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^art[ií]culo\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^producto\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^(?:品名|物料)\s*[:#：-]?\s*(.*)$"),
]

QUANTITY_LABEL_PATTERNS = [
    re.compile(r"(?i)^quantity\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^qty\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^cantidad\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^units?\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^pieces?\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^pcs\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^数量\b[:#：-]?\s*(.*)$"),
]

DATE_LABEL_PATTERNS = [
    re.compile(r"(?i)^date\b[:#-]?\s*(.*)$"),
    re.compile(r"(?i)^fecha\b[:#-]?\s*(.*)$"),
]

GENERIC_LOCATION_KEYWORDS = (
    "warehouse",
    "bodega",
    "almacen",
    "almacén",
    "dc",
    "distribution center",
    "centro de distribucion",
    "centro de distribución",
    "仓",
    "仓库",
    "库房",
    "配送中心",
)

PURE_NUMBER_PATTERN = re.compile(r"^[0-9][0-9,./-]*$")


@dataclass(frozen=True)
class _CandidateLine:
    line: str
    index: int
    score: int


def parse_delivery_ocr_text(raw_text: str) -> DeliveryImageExtractionResponse:
    normalized_text = normalize_text(raw_text)
    lines = [line for line in normalized_text.splitlines() if line]

    return DeliveryImageExtractionResponse(
        item_name=extract_item_name(normalized_text, lines),
        quantity=extract_quantity(normalized_text, lines),
        date=extract_date(lines),
        location=extract_location(lines),
        po_number=extract_po_number(normalized_text, lines),
        entry_note=extract_entry_note(normalized_text, lines),
        raw_text=normalized_text,
    )


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    return "\n".join(
        normalized_line
        for normalized_line in (
            line.strip().replace("\u3000", " ").replace("：", ":")
            for line in value.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        )
        if normalized_line
    )


def extract_po_number(text: str, lines: list[str]) -> str | None:
    labeled_value = _extract_labeled_value(lines, PO_LABEL_PATTERNS)
    if labeled_value:
        return _extract_first_match(labeled_value, [re.compile(r"([A-Z0-9-]{4,})", re.IGNORECASE)]) or labeled_value
    return _extract_first_match(text, PO_PATTERNS)


def extract_entry_note(text: str, lines: list[str]) -> str | None:
    labeled_value = _extract_labeled_value(lines, ENTRY_NOTE_LABEL_PATTERNS)
    if labeled_value:
        return _extract_first_match(labeled_value, [re.compile(r"([A-Z0-9-]{2,})", re.IGNORECASE)]) or labeled_value
    return _extract_first_match(text, ENTRY_NOTE_PATTERNS)


def extract_item_name(text: str, lines: list[str]) -> str | None:
    value = _extract_labeled_value(lines, ITEM_LABEL_PATTERNS) or _extract_first_match(text, ITEM_PATTERNS)
    if value is None:
        return None
    cleaned_value = _strip_inline_noise(value)
    return cleaned_value or None


def extract_quantity(text: str, lines: list[str]) -> int | None:
    value = _extract_labeled_value(lines, QUANTITY_LABEL_PATTERNS) or _extract_first_match(text, QUANTITY_PATTERNS)
    if value is None:
        return None
    matched_numeric_value = _extract_first_match(value, [re.compile(r"([0-9][0-9,]*)")])
    if matched_numeric_value is not None:
        value = matched_numeric_value
    try:
        return int(value.replace(",", ""))
    except ValueError:
        return None


def extract_date(lines: list[str]) -> str | None:
    if not lines:
        return None

    labeled_value = _extract_labeled_value(lines, DATE_LABEL_PATTERNS)
    if labeled_value:
        matched_date = _extract_date_token(labeled_value)
        if matched_date:
            return matched_date

    for line in lines[:5]:
        matched_date = _extract_date_token(line)
        if matched_date:
            return matched_date

    return None


def extract_location(lines: list[str]) -> str | None:
    labeled_value = _extract_labeled_value(lines, LOCATION_LABEL_PATTERNS)
    if labeled_value:
        return labeled_value

    candidates: list[_CandidateLine] = []

    for index, line in enumerate(lines):
        if not _looks_like_location_candidate(line):
            continue

        score = 0
        lower_line = line.lower()

        if index == 0:
            score += 4
        elif index < 3:
            score += 2

        if any(keyword in lower_line for keyword in GENERIC_LOCATION_KEYWORDS):
            score += 5

        if _contains_letters_or_cjk(line):
            score += 2

        if not any(char.isdigit() for char in line):
            score += 1

        if len(line.split()) <= 8 and len(line) <= 64:
            score += 1

        candidates.append(_CandidateLine(line=line, index=index, score=score))

    if not candidates:
        return None

    best_candidate = max(candidates, key=lambda candidate: (candidate.score, -candidate.index))
    return best_candidate.line


def _extract_first_match(text: str, patterns: list[re.Pattern[str]]) -> str | None:
    for pattern in patterns:
        match = pattern.search(text)
        if match:
            return match.group(1).strip()
    return None


def _extract_labeled_value(lines: list[str], label_patterns: list[re.Pattern[str]]) -> str | None:
    for index, line in enumerate(lines):
        for pattern in label_patterns:
            match = pattern.match(line.strip())
            if not match:
                continue

            inline_value = match.group(1).strip(" :#-\t")
            if inline_value:
                return inline_value

            next_value = _next_value_line(lines, index + 1)
            if next_value is not None:
                return next_value

    return None


def _next_value_line(lines: list[str], start_index: int) -> str | None:
    for index in range(start_index, min(len(lines), start_index + 3)):
        candidate = lines[index].strip(" :#-\t")
        if not candidate:
            continue
        if FIELD_PREFIX_PATTERN.match(candidate):
            return None
        return candidate
    return None


def _extract_date_token(text: str) -> str | None:
    for pattern in DATE_PATTERNS:
        match = pattern.search(text)
        if match:
            return match.group(1)
    return None


def _looks_like_location_candidate(line: str) -> bool:
    if not line or FIELD_PREFIX_PATTERN.match(line):
        return False

    if PURE_NUMBER_PATTERN.fullmatch(line):
        return False

    if _extract_date_token(line) == line:
        return False

    if not _contains_letters_or_cjk(line):
        return False

    return True


def _contains_letters_or_cjk(value: str) -> bool:
    for char in value:
        if char.isalpha() or _is_cjk(char):
            return True
    return False


def _is_cjk(char: str) -> bool:
    return "\u4e00" <= char <= "\u9fff"


def _strip_inline_noise(value: str) -> str:
    cleaned_value = value.strip()
    for token in (
        "Quantity:",
        "Qty:",
        "Cantidad:",
        "PO:",
        "Entry Note:",
        "Delivery Note:",
        "Orden de compra:",
        "Nota de entrada:",
        "Nota de entrega:",
        "Ubicación:",
        "Fecha:",
        "Artículo:",
    ):
        cleaned_value = cleaned_value.replace(token, "").strip()
    return cleaned_value
