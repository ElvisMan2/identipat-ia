CREATE TABLE analysis_admissions (
    admission_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    analysis_id UUID,
    input_type VARCHAR(16) NOT NULL,
    actor_mode VARCHAR(24) NOT NULL,
    state VARCHAR(16) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    CONSTRAINT fk_analysis_admissions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_analysis_admissions_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT uk_analysis_admissions_analysis UNIQUE (analysis_id),
    CONSTRAINT ck_analysis_admissions_input_type
        CHECK (input_type IN ('TEXT', 'PDF', 'AUDIO')),
    CONSTRAINT ck_analysis_admissions_actor_mode
        CHECK (actor_mode IN ('STANDARD_SESSION', 'ADMIN_JWT')),
    CONSTRAINT ck_analysis_admissions_state
        CHECK (state IN ('RESERVED', 'ACCEPTED', 'RELEASED')),
    CONSTRAINT ck_analysis_admissions_expiry
        CHECK (expires_at > reserved_at),
    CONSTRAINT ck_analysis_admissions_state_fields
        CHECK (
            (state = 'RESERVED'
                AND analysis_id IS NULL AND accepted_at IS NULL AND released_at IS NULL)
            OR
            (state = 'ACCEPTED'
                AND analysis_id IS NOT NULL AND accepted_at IS NOT NULL AND released_at IS NULL)
            OR
            (state = 'RELEASED'
                AND analysis_id IS NULL AND accepted_at IS NULL AND released_at IS NOT NULL)
        )
);

CREATE INDEX idx_analysis_admissions_user_reserved
    ON analysis_admissions (user_id, reserved_at);
CREATE INDEX idx_analysis_admissions_type_state_reserved
    ON analysis_admissions (input_type, state, reserved_at);
CREATE INDEX idx_analysis_admissions_active_reservations
    ON analysis_admissions (expires_at)
    WHERE state = 'RESERVED';

-- Compatibility at deployment: recent analyses continue to count in rolling
-- quotas, while every non-terminal historical analysis continues to occupy a
-- capacity slot. analysis_id is reused only as a deterministic backfill key.
INSERT INTO analysis_admissions (
    admission_id, user_id, analysis_id, input_type, actor_mode, state,
    reserved_at, expires_at, accepted_at
)
SELECT analysis_id, user_id, analysis_id, input_type,
       CASE WHEN session_id IS NULL THEN 'ADMIN_JWT' ELSE 'STANDARD_SESSION' END,
       'ACCEPTED', created_at, created_at + INTERVAL '8 minutes', created_at
FROM analyses
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
   OR status IN ('RECEIVED', 'PREPROCESSING', 'ANALYZING');
