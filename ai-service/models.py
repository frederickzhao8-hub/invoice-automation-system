from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class AnalyzeOrderRequest(BaseModel):
    order_id: int = Field(..., alias="orderId", gt=0)

    model_config = {"populate_by_name": True}


class SummaryRequest(BaseModel):
    include_recommendations: bool = Field(True, alias="includeRecommendations")

    model_config = {"populate_by_name": True}


class RecommendationsRequest(BaseModel):
    scope: Literal["daily", "weekly", "summary"] = "daily"


class AIInsightResponse(BaseModel):
    analysis_type: str = Field(..., alias="analysisType")
    generated_at: str = Field(..., alias="generatedAt")
    narrative: str
    key_findings: list[str] = Field(..., alias="keyFindings")
    most_common_bottleneck_stage: str | None = Field(None, alias="mostCommonBottleneckStage")
    recommended_actions: list[str] = Field(..., alias="recommendedActions")
    grounding: dict[str, Any]

    model_config = {"populate_by_name": True}


class OrderAnalysisResponse(BaseModel):
    analysis_type: str = Field(..., alias="analysisType")
    generated_at: str = Field(..., alias="generatedAt")
    order_id: int = Field(..., alias="orderId")
    order_number: str = Field(..., alias="orderNumber")
    health_status: str = Field(..., alias="healthStatus")
    delay_stage: str | None = Field(None, alias="delayStage")
    narrative: str
    root_causes: list[str] = Field(..., alias="rootCauses")
    recommended_actions: list[str] = Field(..., alias="recommendedActions")
    grounding: dict[str, Any]

    model_config = {"populate_by_name": True}
