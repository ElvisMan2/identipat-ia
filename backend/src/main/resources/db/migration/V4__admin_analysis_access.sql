ALTER TABLE analyses
    ALTER COLUMN session_id DROP NOT NULL;

ALTER TABLE analyses
    ALTER COLUMN consent_event_id DROP NOT NULL;

ALTER TABLE analyses
    ADD CONSTRAINT ck_analyses_access_context
        CHECK (
            (session_id IS NOT NULL AND consent_event_id IS NOT NULL)
            OR
            (session_id IS NULL AND consent_event_id IS NULL)
        );
