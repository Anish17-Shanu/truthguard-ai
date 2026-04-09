from typing import Literal

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    text: str | None = None
    url: str | None = None
    fileBase64: str | None = None
    fileName: str | None = None


class AnalyzeResponse(BaseModel):
    model_config = {"protected_namespaces": ()}

    label: Literal["fake", "real", "propaganda"] = Field(...)
    confidence: float = Field(..., ge=0, le=1)
    bias_score: float = Field(..., ge=0, le=1)
    emotion: str
    language: str
    highlighted_text: list[str]
    suspicious_phrases: list[str]
    model_version: str


class FeedbackRequest(BaseModel):
    analysis_id: str
    correct: bool
    corrected_label: str | None = None
    comment: str | None = None


class RetrainResponse(BaseModel):
    status: str
    new_model_version: str
