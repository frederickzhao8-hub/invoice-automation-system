# delivery-image-service

Python module and FastAPI service for extracting logistics fields from delivery photos.

## System Design

The module is intentionally split into four layers so it can be embedded into an existing backend or run as a sidecar service:

1. `ocr_provider.py`
   Wraps the OCR engine behind a small interface. The default implementation uses Tesseract via `pytesseract`.
2. `text_parser.py`
   Converts raw OCR text into structured logistics fields with pure parsing logic and no HTTP dependencies.
3. `pipeline.py`
   Orchestrates file validation, OCR execution, and field extraction.
4. `app.py`
   Exposes the pipeline through FastAPI for easy integration from Java, Node, or another Python backend.

## OCR + Field Extraction Pipeline

`image upload -> validate image type -> OCR text extraction -> text normalization -> field parsing -> JSON response`

The pipeline returns:

- `item_name`
- `quantity`
- `date`
- `location`
- `po_number`
- `entry_note`
- `raw_text`

If a field cannot be identified, the parser returns `null`.

## Dynamic Location Extraction Strategy

The location extractor does **not** use a fixed warehouse name list.

Instead it:

1. Splits OCR text into normalized lines.
2. Removes obvious field lines such as `PO`, `Entry Note`, `Item`, `Quantity`, and pure numeric/date lines.
3. Scores remaining lines based on context:
   - appears near the top of the image
   - contains generic location terms such as `warehouse`, `bodega`, `almacen`, `dc`, `distribution center`, `仓`, `仓库`, `库房`
   - contains letters or CJK characters
   - looks like a short human-readable place label instead of an ID
4. Picks the highest scoring candidate as `location`.

This keeps the extraction dynamic and works with English, Spanish, and Chinese warehouse text as long as OCR captures the line.

## Recommended Project Structure

```text
delivery-image-service/
  app.py
  config.py
  models.py
  ocr_provider.py
  pipeline.py
  text_parser.py
  requirements.txt
  test_text_parser.py
  README.md
```

## API Design

### Health

`GET /health`

Response:

```json
{
  "status": "ok"
}
```

### Extract delivery image fields

`POST /delivery-images/extract`

Content type:

`multipart/form-data`

Form field:

- `file`: jpg / jpeg / png image

Response:

```json
{
  "item_name": "ONU",
  "quantity": 50000,
  "date": "1.30",
  "location": "Mexico City Warehouse",
  "po_number": "20250123001",
  "entry_note": "EN-001",
  "raw_text": "OCR extracted text"
}
```

## JSON Schema

```json
{
  "type": "object",
  "properties": {
    "item_name": { "type": ["string", "null"] },
    "quantity": { "type": ["integer", "null"] },
    "date": { "type": ["string", "null"] },
    "location": { "type": ["string", "null"] },
    "po_number": { "type": ["string", "null"] },
    "entry_note": { "type": ["string", "null"] },
    "raw_text": { "type": "string" }
  },
  "required": [
    "item_name",
    "quantity",
    "date",
    "location",
    "po_number",
    "entry_note",
    "raw_text"
  ]
}
```

## Example OCR Input

```text
Mexico City Warehouse
1.30

PO: 20250123001
Entry Note: EN-001

Item: ONU
Quantity: 50000
```

## Example Output

```json
{
  "item_name": "ONU",
  "quantity": 50000,
  "date": "1.30",
  "location": "Mexico City Warehouse",
  "po_number": "20250123001",
  "entry_note": "EN-001",
  "raw_text": "Mexico City Warehouse\n1.30\nPO: 20250123001\nEntry Note: EN-001\nItem: ONU\nQuantity: 50000"
}
```

## Run Locally

```bash
cd delivery-image-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --reload --port 8010
```

## Notes

- The default OCR provider requires Tesseract to be installed on the host system.
- The parsing layer is independent from FastAPI, so an existing Java/Spring backend can call this service over HTTP or embed the parser pipeline behind a job worker.
