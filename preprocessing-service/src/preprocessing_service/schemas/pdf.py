"""Stable schemas for the private PDF extraction contract."""

from pydantic import BaseModel, ConfigDict, Field


class PdfExtractionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    text: str
    page_count: int = Field(serialization_alias="pageCount")
    character_count: int = Field(serialization_alias="characterCount")
    extraction_method: str = Field(default="TEXT_LAYER", serialization_alias="extractionMethod")


class PdfInspectionResponse(BaseModel):
    """Metadata-only inspection result; document text is intentionally omitted."""

    model_config = ConfigDict(populate_by_name=True)

    page_count: int = Field(serialization_alias="pageCount")
    has_text_layer: bool = Field(serialization_alias="hasTextLayer")
    character_count: int = Field(serialization_alias="characterCount")
    extraction_method: str = Field(serialization_alias="extractionMethod")


class PdfErrorResponse(BaseModel):
    code: str
    message: str
