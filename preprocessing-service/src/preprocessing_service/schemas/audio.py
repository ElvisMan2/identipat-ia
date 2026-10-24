"""Stable schemas for the private audio transcription contract."""

from pydantic import BaseModel, ConfigDict, Field


class AudioTranscriptionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    text: str
    duration_seconds: float = Field(serialization_alias="durationSeconds")
    character_count: int = Field(serialization_alias="characterCount")
    language: str
    transcription_method: str = Field(
        default="FASTER_WHISPER",
        serialization_alias="transcriptionMethod",
    )
    model: str


class AudioHealthResponse(BaseModel):
    status: str = "UP"
    service: str = "audio-transcription"
    model: str


class AudioErrorResponse(BaseModel):
    code: str
    message: str
