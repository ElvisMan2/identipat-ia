"""FastAPI application bootstrap."""

import logging

from fastapi import FastAPI

from preprocessing_service.api.health import router as health_router
from preprocessing_service.api.pdf import router as pdf_router
from preprocessing_service.core.config import Settings


def create_app(settings: Settings | None = None) -> FastAPI:
    """Create the private technical preprocessing service."""
    service_settings = settings or Settings()
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
    )
    app.state.settings = service_settings
    app.include_router(health_router)
    app.include_router(pdf_router)
    return app


app = create_app()

