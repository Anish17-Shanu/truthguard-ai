import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.core.config import settings
from app.services.artifacts import ensure_artifacts
from app.services.model_manager import ModelManager
from app.services.nlp_pipeline import NlpPipeline

logger = logging.getLogger(__name__)


def _validate_settings() -> None:
    if not settings.is_production:
        return
    if not settings.internal_token or len(settings.internal_token) < 24:
        raise RuntimeError("INTERNAL_TOKEN must be at least 24 characters in production")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    _validate_settings()

    try:
        ensure_artifacts()
    except Exception as exc:
        if settings.is_production:
            raise
        logger.warning("Model artifact download skipped: %s", exc)

    pipeline = NlpPipeline()
    if pipeline.mode == "heuristic" and settings.is_production and not settings.allow_heuristic_fallback:
        raise RuntimeError(
            "No ONNX model artifact is available. Train and export a model, or set "
            "ALLOW_HEURISTIC_FALLBACK=true to explicitly serve degraded heuristic results."
        )
    app.state.pipeline = pipeline

    manager = ModelManager()
    manager.ensure_indexes()
    app.state.model_manager = manager

    logger.info(
        "TruthGuard AI service started mode=%s version=%s env=%s",
        pipeline.mode,
        pipeline.version,
        settings.app_env,
    )
    try:
        yield
    finally:
        manager.close()


app = FastAPI(
    title=settings.app_name,
    version="1.0.0",
    docs_url=None if settings.is_production else "/docs",
    redoc_url=None,
    openapi_url=None if settings.is_production else "/openapi.json",
    lifespan=lifespan,
)


@app.middleware("http")
async def request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID") or uuid.uuid4().hex
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    return response


@app.exception_handler(Exception)
async def unhandled_exception(request: Request, exc: Exception) -> JSONResponse:
    request_id = getattr(request.state, "request_id", "-")
    logger.exception("Unhandled error request_id=%s", request_id)
    return JSONResponse(
        status_code=500,
        content={
            "code": "internal_error",
            "message": "Internal server error",
            "request_id": request_id,
        },
    )


app.include_router(router)
