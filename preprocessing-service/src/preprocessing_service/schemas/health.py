"""Stable health response schema."""

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str = "UP"
    service: str = "preprocessing-service"
    version: str = "0.1.0"

