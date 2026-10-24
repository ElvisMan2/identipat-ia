"""PDF text-layer extraction without OCR or external services."""

from io import BytesIO

from pypdf import PdfReader
from pypdf.errors import PdfReadError

from preprocessing_service.schemas.pdf import PdfExtractionResponse

PDF_SIGNATURE = b"%PDF-"


class PdfExtractionError(Exception):
    """Controlled PDF processing failure safe to map to the internal API."""

    def __init__(self, code: str, message: str, status_code: int = 422) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


def extract_pdf(data: bytes, max_pages: int) -> PdfExtractionResponse:
    """Extract ordered text from a digital PDF using only its embedded text layer."""
    if not data.startswith(PDF_SIGNATURE):
        raise PdfExtractionError("INVALID_PDF", "The uploaded file is not a valid PDF")

    try:
        reader = PdfReader(BytesIO(data), strict=True)
        if reader.is_encrypted:
            raise PdfExtractionError(
                "PDF_ENCRYPTED",
                "Encrypted or password-protected PDFs are not supported",
            )
        page_count = len(reader.pages)
        if page_count > max_pages:
            raise PdfExtractionError(
                "PDF_PAGE_LIMIT_EXCEEDED",
                "The PDF exceeds the configured page limit",
            )

        page_texts = []
        for page in reader.pages:
            extracted = page.extract_text() or ""
            page_texts.append(_normalize_technical_text(extracted))
    except PdfExtractionError:
        raise
    except (PdfReadError, ValueError, TypeError, KeyError, IndexError) as exception:
        raise PdfExtractionError("INVALID_PDF", "The PDF could not be read") from exception
    except Exception as exception:  # Defensive boundary around untrusted parser input.
        raise PdfExtractionError("INVALID_PDF", "The PDF could not be read") from exception

    text = "\n\n".join(page_texts).strip()
    if not text:
        raise PdfExtractionError(
            "PDF_TEXT_NOT_EXTRACTABLE",
            "The PDF does not contain an extractable text layer",
        )
    return PdfExtractionResponse(
        text=text,
        page_count=page_count,
        character_count=len(text),
    )


def _normalize_technical_text(text: str) -> str:
    return text.replace("\x00", "").replace("\r\n", "\n").replace("\r", "\n").strip()
