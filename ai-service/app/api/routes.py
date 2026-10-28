import logging
import secrets

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pymongo.errors import PyMongoError

from app.core.config import settings
from app.models.schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    CurrentModelResponse,
    FeedbackRequest,
    RetrainResponse,
)
from app.services.ingestion import extract_text_from_file, extract_text_from_url
from app.services.language import detect_language
from app.services.model_manager import ModelManager
from app.services.nlp_pipeline import NlpPipeline

logger = logging.getLogger(__name__)
router = APIRouter()


def require_internal_token(request: Request) -> None:
    expected = settings.internal_token
    if not expected:
        if settings.is_production:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "AI service is not configured")
        return
    provided = request.headers.get("X-Internal-Token", "")
    if not secrets.compare_digest(provided, expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Unauthorized")


def get_pipeline(request: Request) -> NlpPipeline:
    pipeline = getattr(request.app.state, "pipeline", None)
    if pipeline is None:
        pipeline = NlpPipeline()
        request.app.state.pipeline = pipeline
    return pipeline


def get_model_manager(request: Request) -> ModelManager:
    manager = getattr(request.app.state, "model_manager", None)
    if manager is None:
        manager = ModelManager()
        request.app.state.model_manager = manager
    return manager


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": settings.app_name}


@router.get("/ready")
def ready(pipeline: NlpPipeline = Depends(get_pipeline)) -> dict[str, object]:
    return {
        "ready": True,
        "classifier_mode": pipeline.mode,
        "model_version": pipeline.version,
    }


@router.post(
    "/analyze",
    response_model=AnalyzeResponse,
    dependencies=[Depends(require_internal_token)],
)
def analyze(request: AnalyzeRequest, pipeline: NlpPipeline = Depends(get_pipeline)) -> AnalyzeResponse:
    has_text = bool(request.text and request.text.strip())
    has_url = bool(request.url and request.url.strip())
    has_file = bool(request.fileBase64)
    if sum((has_text, has_url, has_file)) != 1:
        raise HTTPException(
            status.HTTP_400_BAD_REQUEST,
            "Provide exactly one of text, url, or file",
        )

    if request.fileBase64:
        if not request.fileName:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "fileName is required for file analysis")
        max_base64 = settings.max_file_mb * 1024 * 1024 * 4 // 3 + 8
        if len(request.fileBase64) > max_base64:
            raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "File is too large")

    if has_url:
        if len(request.url or "") > 2048:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "URL is too long")
        try:
            content = extract_text_from_url(request.url or "")
        except ValueError as exc:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, str(exc)) from exc
    elif has_file:
        try:
            content = extract_text_from_file(request.fileBase64 or "", request.fileName)
        except ValueError as exc:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, str(exc)) from exc
    else:
        content = request.text or ""

    content = content.strip()
    if len(content) < settings.min_chars:
        raise HTTPException(
            status.HTTP_400_BAD_REQUEST,
            f"Content must be at least {settings.min_chars} characters",
        )
    if len(content) > settings.max_chars:
        raise HTTPException(
            status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            f"Content exceeds {settings.max_chars} characters",
        )

    language = detect_language(content)
    if settings.require_english and language not in {"en", "unknown"}:
        raise HTTPException(
            status.HTTP_400_BAD_REQUEST,
            "Only English-language content is supported at this time",
        )

    result = pipeline.analyze(content)
    return AnalyzeResponse(
        label=result.label,
        confidence=result.confidence,
        bias_score=result.bias_score,
        emotion=result.emotion,
        language=result.language,
        highlighted_text=result.highlighted_text,
        suspicious_phrases=result.suspicious_phrases,
        model_version=result.model_version,
        classifier_mode=result.classifier_mode,
        content_hash=result.content_hash,
    )


@router.post(
    "/feedback",
    status_code=status.HTTP_202_ACCEPTED,
    dependencies=[Depends(require_internal_token)],
)
def feedback(request: FeedbackRequest, manager: ModelManager = Depends(get_model_manager)) -> dict[str, str]:
    try:
        manager.save_feedback(request.model_dump())
    except PyMongoError as exc:
        logger.warning("Feedback persistence failed: %s", exc)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Feedback store unavailable") from exc
    return {"status": "accepted"}


@router.post(
    "/retrain",
    response_model=RetrainResponse,
    status_code=status.HTTP_202_ACCEPTED,
    dependencies=[Depends(require_internal_token)],
)
def retrain(manager: ModelManager = Depends(get_model_manager)) -> RetrainResponse:
    if not settings.retrain_enabled:
        raise HTTPException(
            status.HTTP_501_NOT_IMPLEMENTED,
            "Retraining is disabled; run the offline training pipeline instead",
        )
    try:
        request_id = manager.request_retrain("admin-api")
    except PyMongoError as exc:
        logger.warning("Could not queue retrain request: %s", exc)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Retrain queue unavailable") from exc
    return RetrainResponse(
        status="queued",
        message=f"Retraining request {request_id} queued for the offline pipeline",
    )


@router.get(
    "/models/current",
    response_model=CurrentModelResponse,
    dependencies=[Depends(require_internal_token)],
)
def current_model(
    pipeline: NlpPipeline = Depends(get_pipeline),
    manager: ModelManager = Depends(get_model_manager),
) -> CurrentModelResponse:
    metadata = pipeline.metadata()
    metrics = metadata.get("metrics") or {}
    if not metrics:
        metrics = manager.registered_metrics(str(metadata["version"]))
    trained_at = metadata.get("trained_at")
    return CurrentModelResponse(
        version=str(metadata["version"]),
        classifier_mode=str(metadata["classifier_mode"]),
        metrics=metrics,
        trained_at=str(trained_at) if trained_at else None,
    )


@router.delete(
    "/internal/users/{user_id}/data",
    dependencies=[Depends(require_internal_token)],
)
def delete_user_data(user_id: str, manager: ModelManager = Depends(get_model_manager)) -> dict[str, object]:
    if not user_id.isalnum():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Invalid user id")
    try:
        deleted = manager.delete_user_data(user_id)
    except PyMongoError as exc:
        logger.warning("Could not delete AI data for user %s: %s", user_id, exc)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Data store unavailable") from exc
    return {"status": "deleted", "feedbackRemoved": deleted}
