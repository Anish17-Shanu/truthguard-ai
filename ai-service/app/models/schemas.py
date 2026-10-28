from typing import Literal

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    text: str | None = None
    url: str | None = None
    fileBase64: str | None = None
    fileName: str | None = None


class AnalyzeResponse(BaseModel):
    model_config = {"protected_namespaces": ()}

    label: Literal["real", "fake", "propaganda", "uncertain"] = Field(...)
    confidence: float = Field(..., ge=0, le=1)
    bias_score: float = Field(..., ge=0, le=1)
    emotion: str
    language: str
    highlighted_text: list[str]
    suspicious_phrases: list[str]
    model_version: str
    classifier_mode: Literal["transformer", "heuristic"] = Field(...)
    content_hash: str


class FeedbackRequest(BaseModel):
    analysis_id: str
    user_id: str | None = None
    correct: bool
    corrected_label: str | None = None
    comment: str | None = None
    model_version: str | None = None
    content_hash: str | None = None


class CurrentModelResponse(BaseModel):
    model_config = {"protected_namespaces": ()}

    version: str
    classifier_mode: str
    metrics: dict[str, float] = Field(default_factory=dict)
    trained_at: str | None = None


class RetrainResponse(BaseModel):
    status: str
    new_model_version: str | None = None
    message: str | None = None
