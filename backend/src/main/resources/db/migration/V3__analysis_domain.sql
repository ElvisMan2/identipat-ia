ALTER TABLE consent_events
    ADD CONSTRAINT uk_consent_events_event_session_user
        UNIQUE (consent_event_id, session_id, user_id);

CREATE TABLE analyses (
    analysis_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id UUID NOT NULL,
    consent_event_id UUID NOT NULL,
    input_type VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(512),
    next_attempt_at TIMESTAMPTZ,
    lease_owner VARCHAR(80),
    lease_until TIMESTAMPTZ,
    CONSTRAINT fk_analyses_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_analyses_session_user
        FOREIGN KEY (session_id, user_id)
        REFERENCES standard_sessions (session_id, user_id),
    CONSTRAINT fk_analyses_consent_session_user
        FOREIGN KEY (consent_event_id, session_id, user_id)
        REFERENCES consent_events (consent_event_id, session_id, user_id),
    CONSTRAINT ck_analyses_input_type
        CHECK (input_type IN ('TEXT', 'PDF', 'AUDIO')),
    CONSTRAINT ck_analyses_status
        CHECK (status IN ('RECEIVED', 'PREPROCESSING', 'ANALYZING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_analyses_lease_pair
        CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CONSTRAINT ck_analyses_terminal_timestamps
        CHECK ((status <> 'COMPLETED' OR completed_at IS NOT NULL)
           AND (status <> 'FAILED' OR failed_at IS NOT NULL))
);

CREATE INDEX idx_analyses_id_session ON analyses (analysis_id, session_id);
CREATE INDEX idx_analyses_claim
    ON analyses (next_attempt_at, lease_until, created_at)
    WHERE status IN ('RECEIVED', 'ANALYZING');

CREATE TABLE analysis_inputs (
    analysis_id UUID PRIMARY KEY,
    original_text TEXT,
    processed_text TEXT,
    source_metadata JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT fk_analysis_inputs_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT ck_analysis_inputs_source_metadata_object
        CHECK (jsonb_typeof(source_metadata) = 'object')
);

CREATE TABLE ai_invocations (
    invocation_id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(64),
    model VARCHAR(255),
    provider_request_id VARCHAR(255),
    prompt_id VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    template_hash VARCHAR(64) NOT NULL,
    rendered_hash VARCHAR(64) NOT NULL,
    rendered_prompt_snapshot TEXT NOT NULL,
    output_schema_id VARCHAR(128) NOT NULL,
    output_schema_version VARCHAR(64) NOT NULL,
    request_parameters JSONB,
    raw_provider_response JSONB,
    structured_provider_response JSONB,
    input_tokens BIGINT,
    output_tokens BIGINT,
    total_tokens BIGINT,
    token_usage JSONB,
    latency_ms BIGINT,
    finish_reason VARCHAR(255),
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    retryable BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_ai_invocations_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT uk_ai_invocations_analysis_attempt UNIQUE (analysis_id, attempt_number),
    CONSTRAINT ck_ai_invocations_attempt_positive CHECK (attempt_number > 0),
    CONSTRAINT ck_ai_invocations_status
        CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'INVALID_RESPONSE', 'ABANDONED')),
    CONSTRAINT ck_ai_invocations_hashes
        CHECK (template_hash ~ '^[0-9a-f]{64}$' AND rendered_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_invocations_token_counts
        CHECK ((input_tokens IS NULL OR input_tokens >= 0)
           AND (output_tokens IS NULL OR output_tokens >= 0)
           AND (total_tokens IS NULL OR total_tokens >= 0)
           AND (latency_ms IS NULL OR latency_ms >= 0))
);

CREATE INDEX idx_ai_invocations_analysis_id ON ai_invocations (analysis_id);

CREATE TABLE analysis_results (
    analysis_id UUID PRIMARY KEY,
    schema_version VARCHAR(128) NOT NULL,
    result_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_analysis_results_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT ck_analysis_results_json_object
        CHECK (jsonb_typeof(result_json) = 'object')
);
