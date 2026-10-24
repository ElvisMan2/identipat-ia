"""Private local audio transcription endpoints consumed only by the Java backend."""

from typing import Annotated

from fastapi import APIRouter, File, Request, UploadFile
from fastapi.responses import JSONResponse

from preprocessing_service.core.config import Settings
from preprocessing_service.schemas.audio import (
    AudioErrorResponse,
    AudioHealthResponse,
    AudioTranscriptionResponse,
)
from preprocessing_service.services.audio_transcriber import (
    AudioProcessingError,
    AudioTranscriptionService,
)

router = APIRouter(prefix="/v1/audio", tags=["audio"])

SUPPORTED_AUDIO_MEDIA_TYPES = {
    "audio/webm",
    "audio/mpeg",
    "audio/mp4",
    "audio/x-m4a",
    "audio/wav",
    "audio/x-wav",
    "audio/ogg",
    "audio/flac",
}


@router.get(
    "/health",
    response_model=AudioHealthResponse,
    responses={503: {"model": AudioErrorResponse}},
)
def audio_health(request: Request) -> AudioHealthResponse | JSONResponse:
    service: AudioTranscriptionService = request.app.state.audio_service
    settings: Settings = request.app.state.settings
    if not service.is_ready():
        return _error(503, "AUDIO_MODEL_NOT_READY", "The audio transcription model is not ready")
    return AudioHealthResponse(model=settings.audio_model)


@router.post(
    "/transcribe",
    response_model=AudioTranscriptionResponse,
    response_model_by_alias=True,
    responses={
        413: {"model": AudioErrorResponse},
        415: {"model": AudioErrorResponse},
        422: {"model": AudioErrorResponse},
        503: {"model": AudioErrorResponse},
    },
)
async def transcribe(
    request: Request,
    file: Annotated[UploadFile | None, File()] = None,
) -> AudioTranscriptionResponse | JSONResponse:
    """Decode and transcribe a short Spanish description without persisting its audio."""
    settings: Settings = request.app.state.settings
    service: AudioTranscriptionService = request.app.state.audio_service
    if file is None:
        return _error(422, "INVALID_AUDIO", "A non-empty audio file is required")
    media_type = _normalized_media_type(file.content_type)
    if media_type not in SUPPORTED_AUDIO_MEDIA_TYPES:
        return _error(
            415,
            "UNSUPPORTED_AUDIO_MEDIA_TYPE",
            "The audio media type is not supported",
        )

    data = await file.read(settings.audio_max_file_size_bytes + 1)
    if len(data) > settings.audio_max_file_size_bytes:
        return _error(413, "AUDIO_TOO_LARGE", "The audio exceeds the configured size limit")
    if not data:
        return _error(422, "INVALID_AUDIO", "A non-empty audio file is required")

    try:
        return service.transcribe(data)
    except AudioProcessingError as exception:
        return _error(exception.status_code, exception.code, exception.message)


def _normalized_media_type(content_type: str | None) -> str:
    return (content_type or "").split(";", maxsplit=1)[0].strip().lower()


def _error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content=AudioErrorResponse(code=code, message=message).model_dump(),
    )
