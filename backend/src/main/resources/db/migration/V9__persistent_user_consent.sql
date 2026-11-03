CREATE TABLE legal_document_history (
    content_sha256 CHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content_html TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_legal_document_history_hash
        CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE legal_document (
    document_id UUID PRIMARY KEY,
    singleton_key SMALLINT NOT NULL DEFAULT 1,
    title VARCHAR(255) NOT NULL,
    content_html TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_legal_document_singleton UNIQUE (singleton_key),
    CONSTRAINT ck_legal_document_singleton CHECK (singleton_key = 1),
    CONSTRAINT ck_legal_document_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_legal_document_history
        FOREIGN KEY (content_sha256) REFERENCES legal_document_history (content_sha256),
    CONSTRAINT fk_legal_document_updated_by
        FOREIGN KEY (updated_by) REFERENCES users (user_id) ON DELETE SET NULL
);

CREATE TABLE user_terms_acceptances (
    acceptance_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    accepted_content_sha256 CHAR(64) NOT NULL,
    accepted_document_updated_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(32) NOT NULL,
    CONSTRAINT fk_user_terms_acceptances_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_terms_acceptances_history
        FOREIGN KEY (accepted_content_sha256)
        REFERENCES legal_document_history (content_sha256),
    CONSTRAINT uk_user_terms_acceptances_user_hash_effective_at
        UNIQUE (user_id, accepted_content_sha256, accepted_document_updated_at),
    CONSTRAINT uk_user_terms_acceptances_id_user
        UNIQUE (acceptance_id, user_id),
    CONSTRAINT ck_user_terms_acceptances_hash
        CHECK (accepted_content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_terms_acceptances_source
        CHECK (source IN ('STANDARD_REGISTRATION', 'STANDARD_WEB'))
);

CREATE INDEX idx_user_terms_acceptances_user_id
    ON user_terms_acceptances (user_id);

ALTER TABLE analyses
    ADD COLUMN terms_acceptance_id UUID;

ALTER TABLE analyses
    ADD CONSTRAINT fk_analyses_terms_acceptance_user
        FOREIGN KEY (terms_acceptance_id, user_id)
        REFERENCES user_terms_acceptances (acceptance_id, user_id);

ALTER TABLE analyses
    DROP CONSTRAINT ck_analyses_access_context;

-- consent_event_id remains only for pre-F1.1B rows. New code writes terms_acceptance_id.
ALTER TABLE analyses
    ADD CONSTRAINT ck_analyses_access_context
        CHECK (
            (session_id IS NULL AND consent_event_id IS NULL AND terms_acceptance_id IS NULL)
            OR
            (session_id IS NOT NULL AND consent_event_id IS NOT NULL AND terms_acceptance_id IS NULL)
            OR
            (session_id IS NOT NULL AND consent_event_id IS NULL AND terms_acceptance_id IS NOT NULL)
        );

