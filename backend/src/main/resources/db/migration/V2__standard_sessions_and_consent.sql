CREATE TABLE standard_sessions (
    session_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    close_reason VARCHAR(32),
    CONSTRAINT fk_standard_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uk_standard_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_standard_sessions_session_user UNIQUE (session_id, user_id),
    CONSTRAINT ck_standard_sessions_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CLOSED')),
    CONSTRAINT ck_standard_sessions_expires_after_creation
        CHECK (expires_at > created_at),
    CONSTRAINT ck_standard_sessions_absolute_expires_after_creation
        CHECK (absolute_expires_at > created_at),
    CONSTRAINT ck_standard_sessions_expiry_within_absolute
        CHECK (expires_at <= absolute_expires_at)
);

CREATE INDEX idx_standard_sessions_user_id ON standard_sessions (user_id);
CREATE INDEX idx_standard_sessions_expires_at ON standard_sessions (expires_at);
CREATE INDEX idx_standard_sessions_status ON standard_sessions (status);

CREATE TABLE consent_events (
    consent_event_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    consent_version VARCHAR(128) NOT NULL,
    consent_document_hash VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    source VARCHAR(32),
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_consent_events_session_user
        FOREIGN KEY (session_id, user_id)
        REFERENCES standard_sessions (session_id, user_id),
    CONSTRAINT ck_consent_events_decision
        CHECK (decision IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_consent_events_document_hash
        CHECK (consent_document_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uk_consent_events_session_version
        UNIQUE (session_id, consent_version)
);

CREATE INDEX idx_consent_events_user_id ON consent_events (user_id);
CREATE INDEX idx_consent_events_session_id ON consent_events (session_id);
