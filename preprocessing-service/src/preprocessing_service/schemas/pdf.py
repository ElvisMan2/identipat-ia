"""Stable schemas for the private PDF extraction contract."""

from pydantic import BaseModel, ConfigDict, Field


class PdfExtractionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    text: str
    page_count: int = Field(serialization_alias="pageCount")
    character_count: int = Field(serialization_alias="characterCount")
    extraction_method: str = Field(default="TEXT_LAYER", serialization_alias="extractionMethod")


class PdfErrorResponse(BaseModel):
    code: str
    message: str
