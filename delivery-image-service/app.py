from __future__ import annotations

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from config import Settings
from models import DeliveryImageExtractionResponse, HealthResponse
from ocr_provider import TesseractOcrProvider
from pipeline import DeliveryImageExtractionPipeline


SETTINGS = Settings.from_env()
PIPELINE = DeliveryImageExtractionPipeline(
    TesseractOcrProvider(
        lang=SETTINGS.ocr_languages,
        tesseract_cmd=SETTINGS.tesseract_cmd,
    )
)

app = FastAPI(title="delivery-image-service", version="0.0.1")
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(SETTINGS.frontend_origins),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok")


@app.post("/delivery-images/extract", response_model=DeliveryImageExtractionResponse)
async def extract_delivery_image(file: UploadFile = File(...)) -> DeliveryImageExtractionResponse:
    try:
        image_bytes = await file.read()
        return PIPELINE.process_image(file.filename, file.content_type, image_bytes)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime guard
        raise HTTPException(status_code=500, detail="Unable to extract delivery image fields.") from exc


if __name__ == "__main__":
    uvicorn.run(app, host=SETTINGS.host, port=SETTINGS.port)
