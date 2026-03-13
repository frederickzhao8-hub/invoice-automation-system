from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from analysis_engine import InsightAnalysisEngine
from config import Settings
from models import (
    AIInsightResponse,
    AnalyzeOrderRequest,
    OrderAnalysisResponse,
    RecommendationsRequest,
    SummaryRequest,
)
from service_clients import AnalyticsApiClient, ServiceClientError


SETTINGS = Settings.from_env()
ANALYTICS_CLIENT = AnalyticsApiClient(
    SETTINGS.analytics_base_urls,
    SETTINGS.request_timeout_seconds,
)
ENGINE = InsightAnalysisEngine(ANALYTICS_CLIENT)

app = FastAPI(title="ai-service", version="0.0.1")
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(SETTINGS.frontend_origins),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/ai/analyze-order", response_model=OrderAnalysisResponse)
def analyze_order(request: AnalyzeOrderRequest) -> OrderAnalysisResponse:
    try:
        return ENGINE.analyze_order(request.order_id)
    except ServiceClientError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime guard
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/ai/daily-summary", response_model=AIInsightResponse)
def daily_summary(request: SummaryRequest | None = None) -> AIInsightResponse:
    effective_request = request or SummaryRequest()
    try:
        return ENGINE.daily_summary(effective_request.include_recommendations)
    except ServiceClientError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime guard
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/ai/weekly-summary", response_model=AIInsightResponse)
def weekly_summary(request: SummaryRequest | None = None) -> AIInsightResponse:
    effective_request = request or SummaryRequest()
    try:
        return ENGINE.weekly_summary(effective_request.include_recommendations)
    except ServiceClientError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime guard
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/ai/recommendations", response_model=AIInsightResponse)
def recommendations(request: RecommendationsRequest | None = None) -> AIInsightResponse:
    effective_request = request or RecommendationsRequest()
    try:
        return ENGINE.recommendations(effective_request.scope)
    except ServiceClientError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime guard
        raise HTTPException(status_code=500, detail=str(exc)) from exc


if __name__ == "__main__":
    uvicorn.run(app, host=SETTINGS.host, port=SETTINGS.port)
