"""Private PDF extraction endpoint consumed only by the Java backend."""

from typing import Annotated

from fastapi import APIRouter, File, Request, UploadFile
from fastapi.responses import JSONResponse

from preprocessing_service.core.config import Settings
from preprocessing_service.schemas.pdf import PdfErrorResponse, PdfExtractionResponse
from preprocessing_service.services.pdf_extractor import PdfExtractionError, extract_pdf

router = APIRouter(prefix="/v1/pdf", tags=["pdf"])


@router.post(
    "/extract",
    response_model=PdfExtractionResponse,
    response_model_by_alias=True,
    responses={
        413: {"model": PdfErrorResponse},
        415: {"model": PdfErrorResponse},
        422: {"model": PdfErrorResponse},
    },
)
async def extract(
    request: Request,
    file: Annotated[UploadFile | None, File()] = None,
) -> PdfExtractionResponse | JSONResponse:
    """Validate and extract a PDF text layer without persisting or logging its contents."""
    settings: Settings = request.app.state.settings
    if file is None:
        return _error(422, "INVALID_PDF", "A PDF file is required")
    if file.content_type != "application/pdf":
        return _error(415, "UNSUPPORTED_PDF_MEDIA_TYPE", "Only application/pdf is supported")

    data = await file.read(settings.pdf_max_file_size_bytes + 1)
    if len(data) > settings.pdf_max_file_size_bytes:
        return _error(413, "PDF_TOO_LARGE", "The PDF exceeds the configured size limit")

    try:
        return extract_pdf(data, settings.pdf_max_pages)
    except PdfExtractionError as exception:
        return _error(exception.status_code, exception.code, exception.message)


def _error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content=PdfErrorResponse(code=code, message=message).model_dump(),
    )
