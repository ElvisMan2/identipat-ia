CREATE TABLE analysis_idempotency_keys (
    idempotency_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_mode VARCHAR(24) NOT NULL,
    session_id UUID,
    key_digest VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    input_type VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    claim_token UUID,
    analysis_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    claim_expires_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_analysis_idempotency_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_analysis_idempotency_session
        FOREIGN KEY (session_id) REFERENCES standard_sessions (session_id),
    CONSTRAINT fk_analysis_idempotency_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT uk_analysis_idempotency_analysis UNIQUE (analysis_id),
    CONSTRAINT ck_analysis_idempotency_actor_mode
        CHECK (actor_mode IN ('STANDARD_SESSION', 'ADMIN_JWT')),
    CONSTRAINT ck_analysis_idempotency_input_type
        CHECK (input_type IN ('TEXT', 'PDF', 'AUDIO')),
    CONSTRAINT ck_analysis_idempotency_state
        CHECK (state IN ('IN_PROGRESS', 'ACCEPTED', 'RELEASED')),
    CONSTRAINT ck_analysis_idempotency_scope
        CHECK ((actor_mode = 'STANDARD_SESSION' AND session_id IS NOT NULL)
            OR (actor_mode = 'ADMIN_JWT' AND session_id IS NULL)),
    CONSTRAINT ck_analysis_idempotency_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_analysis_idempotency_state_fields
        CHECK (
            (state = 'IN_PROGRESS'
                AND claim_token IS NOT NULL AND claim_expires_at IS NOT NULL
                AND analysis_id IS NULL AND accepted_at IS NULL)
            OR
            (state = 'ACCEPTED'
                AND claim_token IS NULL AND claim_expires_at IS NULL
                AND analysis_id IS NOT NULL AND accepted_at IS NOT NULL)
            OR
            (state = 'RELEASED'
                AND claim_token IS NULL AND claim_expires_at IS NULL
                AND analysis_id IS NULL AND accepted_at IS NULL)
        )
);

CREATE UNIQUE INDEX uk_analysis_idempotency_standard_scope
    ON analysis_idempotency_keys (user_id, session_id, key_digest)
    WHERE actor_mode = 'STANDARD_SESSION';

CREATE UNIQUE INDEX uk_analysis_idempotency_admin_scope
    ON analysis_idempotency_keys (user_id, key_digest)
    WHERE actor_mode = 'ADMIN_JWT';

CREATE INDEX idx_analysis_idempotency_claim_expiry
    ON analysis_idempotency_keys (claim_expires_at)
    WHERE state = 'IN_PROGRESS';

CREATE INDEX idx_analysis_idempotency_retention_expiry
    ON analysis_idempotency_keys (expires_at);
