ALTER TABLE analysis_idempotency_keys
    ADD CONSTRAINT ck_analysis_idempotency_claim_within_retention
    CHECK (state <> 'IN_PROGRESS' OR claim_expires_at < expires_at);
