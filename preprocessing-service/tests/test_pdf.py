from io import BytesIO

from fastapi.testclient import TestClient
from pypdf import PdfWriter

from preprocessing_service.core.config import Settings
from preprocessing_service.main import create_app


def _text_pdf(*page_texts: str) -> bytes:
    objects: dict[int, bytes] = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        3: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    }
    page_ids = []
    for index, text in enumerate(page_texts):
        page_id = 4 + index * 2
        content_id = page_id + 1
        page_ids.append(page_id)
        escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        stream = f"BT /F1 12 Tf 72 720 Td ({escaped}) Tj ET".encode("ascii")
        objects[page_id] = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_id} 0 R >>"
        ).encode("ascii")
        objects[content_id] = (
            f"<< /Length {len(stream)} >>\nstream\n".encode("ascii")
            + stream
            + b"\nendstream"
        )
    kids = " ".join(f"{page_id} 0 R" for page_id in page_ids)
    objects[2] = f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode("ascii")

    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for object_id in range(1, max(objects) + 1):
        offsets.append(len(output))
        output.extend(f"{object_id} 0 obj\n".encode("ascii"))
        output.extend(objects[object_id])
        output.extend(b"\nendobj\n")
    xref = len(output)
    output.extend(f"xref\n0 {len(offsets)}\n".encode("ascii"))
    output.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    output.extend(
        f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode(
            "ascii"
        )
    )
    return bytes(output)


def _writer_pdf(*, encrypted: bool = False) -> bytes:
    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    if encrypted:
        writer.encrypt("secret")
    target = BytesIO()
    writer.write(target)
    return target.getvalue()


def _client(**settings: object) -> TestClient:
    return TestClient(create_app(Settings(**settings)))


def test_extracts_valid_pdf_text_and_metadata() -> None:
    response = _client().post(
        "/v1/pdf/extract",
        files={"file": ("ignored-name.pdf", _text_pdf("Texto digital valido"), "application/pdf")},
    )

    assert response.status_code == 200
    assert response.json() == {
        "text": "Texto digital valido",
        "pageCount": 1,
        "characterCount": 20,
        "extractionMethod": "TEXT_LAYER",
    }


def test_preserves_page_order() -> None:
    response = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", _text_pdf("Pagina uno", "Pagina dos"), "application/pdf")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["pageCount"] == 2
    assert payload["text"].index("Pagina uno") < payload["text"].index("Pagina dos")


def test_rejects_invalid_mime_and_signature() -> None:
    missing = _client().post("/v1/pdf/extract")
    invalid_mime = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", _text_pdf("Texto valido"), "text/plain")},
    )
    invalid_signature = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", b"not a pdf", "application/pdf")},
    )

    assert missing.status_code == 422
    assert missing.json()["code"] == "INVALID_PDF"
    assert invalid_mime.status_code == 415
    assert invalid_mime.json()["code"] == "UNSUPPORTED_PDF_MEDIA_TYPE"
    assert invalid_signature.status_code == 422
    assert invalid_signature.json()["code"] == "INVALID_PDF"


def test_rejects_corrupt_encrypted_page_limit_and_missing_text() -> None:
    corrupt = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", b"%PDF-corrupt", "application/pdf")},
    )
    encrypted = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", _writer_pdf(encrypted=True), "application/pdf")},
    )
    page_limit = _client(pdf_max_pages=1).post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", _text_pdf("Uno", "Dos"), "application/pdf")},
    )
    no_text = _client().post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", _writer_pdf(), "application/pdf")},
    )

    assert (corrupt.status_code, corrupt.json()["code"]) == (422, "INVALID_PDF")
    assert (encrypted.status_code, encrypted.json()["code"]) == (422, "PDF_ENCRYPTED")
    assert (page_limit.status_code, page_limit.json()["code"]) == (
        422,
        "PDF_PAGE_LIMIT_EXCEEDED",
    )
    assert (no_text.status_code, no_text.json()["code"]) == (422, "PDF_TEXT_NOT_EXTRACTABLE")


def test_rejects_oversized_pdf_before_parsing() -> None:
    response = _client(pdf_max_file_size_bytes=7).post(
        "/v1/pdf/extract",
        files={"file": ("document.pdf", b"%PDF-123", "application/pdf")},
    )

    assert response.status_code == 413
    assert response.json()["code"] == "PDF_TOO_LARGE"


def test_error_response_does_not_expose_content_or_sensitive_context() -> None:
    secret = "sensitive-file-name-123.pdf"
    response = _client().post(
        "/v1/pdf/extract",
        files={"file": (secret, b"%PDF-corrupt", "application/pdf")},
        headers={"Authorization": "Bearer should-not-leak"},
    )

    serialized = response.text
    assert response.json().keys() == {"code", "message"}
    assert secret not in serialized
    assert "should-not-leak" not in serialized
