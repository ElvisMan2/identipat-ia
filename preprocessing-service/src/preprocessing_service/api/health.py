"""Health endpoint for orchestration and diagnostics."""

from fastapi import APIRouter

from preprocessing_service.schemas.health import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """Return only the stable, non-sensitive service identity and status."""
    return HealthResponse()

