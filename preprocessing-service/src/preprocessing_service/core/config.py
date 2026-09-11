"""Externalized, non-sensitive service settings."""

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings limited to the HTTP service itself."""

    environment: str = Field(default="dev", validation_alias="PREPROCESSING_ENV")
    host: str = Field(default="127.0.0.1", validation_alias="PREPROCESSING_HOST")
    port: int = Field(default=8090, validation_alias="PREPROCESSING_PORT")
    log_level: str = Field(default="INFO", validation_alias="PREPROCESSING_LOG_LEVEL")

    model_config = SettingsConfigDict(extra="ignore", populate_by_name=True)
