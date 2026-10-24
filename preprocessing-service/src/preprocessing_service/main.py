"""FastAPI application bootstrap."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from preprocessing_service.api.audio import router as audio_router
from preprocessing_service.api.health import router as health_router
from preprocessing_service.api.pdf import router as pdf_router
from preprocessing_service.core.config import Settings
from preprocessing_service.services.audio_transcriber import AudioTranscriptionService


def create_app(
    settings: Settings | None = None,
    audio_service: AudioTranscriptionService | None = None,
) -> FastAPI:
    """Create the private technical preprocessing service."""
    service_settings = settings or Settings()
    service_audio = audio_service or AudioTranscriptionService(service_settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        service_audio.start_warmup()
        yield

    logging.basicConfig(
        level=service_settings.log_level.upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    production = service_settings.environment.lower() == "prod"
    app = FastAPI(
        title="IDENTIPAT-IA preprocessing service",
        version="0.1.0",
        docs_url=None if production else "/docs",
        redoc_url=None if production else "/redoc",
        openapi_url=None if production else "/openapi.json",
        lifespan=lifespan,
    )
    app.state.settings = service_settings
    app.state.audio_service = service_audio
    app.include_router(health_router)
    app.include_router(pdf_router)
    app.include_router(audio_router)
    return app


app = create_app()

