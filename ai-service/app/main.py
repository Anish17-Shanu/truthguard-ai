from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import retraining, router
from app.core.config import settings


@asynccontextmanager
async def lifespan(_: FastAPI):
    retraining.start()
    yield
    retraining.stop()


app = FastAPI(title=settings.app_name, version="1.0.0", lifespan=lifespan)
app.include_router(router)

