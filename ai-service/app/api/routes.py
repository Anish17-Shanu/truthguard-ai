from functools import lru_cache

from fastapi import APIRouter, HTTPException

from app.core.config import settings
from app.models.schemas import AnalyzeRequest, AnalyzeResponse, FeedbackRequest, RetrainResponse
from app.services.ingestion import extract_text_from_file, extract_text_from_url
from app.services.model_manager import ModelManager
from app.services.nlp_pipeline import NlpPipeline
from app.services.retraining import RetrainingService

router = APIRouter()
model_manager = ModelManager()
retraining = RetrainingService(model_manager)


@lru_cache(maxsize=1)
def get_nlp() -> NlpPipeline:
    return NlpPipeline()


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": settings.app_name}


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    if not any([request.text, request.url, request.fileBase64]):
        raise HTTPException(status_code=400, detail="Provide text, url, or file")

    content = request.text
    if request.url:
        content = extract_text_from_url(request.url)
    elif request.fileBase64:
        content = extract_text_from_file(request.fileBase64, request.fileName)

    if not content or len(content.strip()) < 20:
        raise HTTPException(status_code=400, detail="Insufficient content for analysis")

    result = get_nlp().analyze(content)
    return AnalyzeResponse(
        label=result.label,
        confidence=result.confidence,
        bias_score=result.bias_score,
        emotion=result.emotion,
        language=result.language,
        highlighted_text=result.highlighted_text,
        suspicious_phrases=result.suspicious_phrases,
        model_version=model_manager.current_model_version(),
    )


@router.post("/feedback")
def feedback(request: FeedbackRequest) -> dict[str, str]:
    model_manager.save_feedback(request.model_dump())
    return {"status": "accepted"}


@router.post("/retrain", response_model=RetrainResponse)
def retrain() -> RetrainResponse:
    version = retraining.run_retrain_job()
    return RetrainResponse(status="completed", new_model_version=version)


@router.get("/models/current")
def current_model() -> dict[str, str]:
    return {"version": model_manager.current_model_version()}
