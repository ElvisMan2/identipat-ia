"""Externalized, non-sensitive service settings."""

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings limited to the HTTP service itself."""

    environment: str = Field(default="dev", validation_alias="PREPROCESSING_ENV")
    host: str = Field(default="127.0.0.1", validation_alias="PREPROCESSING_HOST")
    port: int = Field(default=8090, validation_alias="PREPROCESSING_PORT")
    log_level: str = Field(default="INFO", validation_alias="PREPROCESSING_LOG_LEVEL")
    pdf_max_file_size_bytes: int = Field(
        default=10_485_760,
        gt=0,
        validation_alias="PREPROCESSING_PDF_MAX_FILE_SIZE_BYTES",
    )
    pdf_max_pages: int = Field(
        default=50,
        gt=0,
        validation_alias="PREPROCESSING_PDF_MAX_PAGES",
    )
    audio_max_file_size_bytes: int = Field(
        default=20_971_520,
        gt=0,
        validation_alias="PREPROCESSING_AUDIO_MAX_FILE_SIZE_BYTES",
    )
    audio_max_duration_seconds: float = Field(
        default=180,
        gt=0,
        validation_alias="PREPROCESSING_AUDIO_MAX_DURATION_SECONDS",
    )
    audio_model: str = Field(default="base", validation_alias="PREPROCESSING_AUDIO_MODEL")
    audio_language: str = Field(default="es", validation_alias="PREPROCESSING_AUDIO_LANGUAGE")
    audio_device: str = Field(default="cpu", validation_alias="PREPROCESSING_AUDIO_DEVICE")
    audio_compute_type: str = Field(
        default="int8",
        validation_alias="PREPROCESSING_AUDIO_COMPUTE_TYPE",
    )
    audio_model_cache_dir: str = Field(
        default="/models",
        validation_alias="PREPROCESSING_AUDIO_MODEL_CACHE_DIR",
    )

    model_config = SettingsConfigDict(extra="ignore", populate_by_name=True)
