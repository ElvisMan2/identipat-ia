CREATE TABLE analysis_pdf_documents (
    analysis_id UUID PRIMARY KEY,
    content BYTEA NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_analysis_pdf_documents_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id) ON DELETE CASCADE,
    CONSTRAINT ck_analysis_pdf_documents_media_type
        CHECK (media_type = 'application/pdf'),
    CONSTRAINT ck_analysis_pdf_documents_size
        CHECK (size_bytes > 0 AND octet_length(content) = size_bytes),
    CONSTRAINT ck_analysis_pdf_documents_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_analysis_pdf_documents_created_at
    ON analysis_pdf_documents (created_at);
