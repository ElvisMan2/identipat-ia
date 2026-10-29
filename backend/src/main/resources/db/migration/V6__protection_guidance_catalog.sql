CREATE TABLE protection_guide_version_counters (
    protection_type VARCHAR(40) PRIMARY KEY,
    next_version BIGINT NOT NULL,
    CONSTRAINT ck_protection_guide_counters_type CHECK (protection_type IN (
        'INVENTION_PATENT', 'UTILITY_MODEL', 'INDUSTRIAL_DESIGN', 'COPYRIGHT',
        'DISTINCTIVE_SIGN', 'PLANT_BREEDER_CERTIFICATE', 'TRADE_SECRET'
    )),
    CONSTRAINT ck_protection_guide_counters_next_version CHECK (next_version > 0)
);

INSERT INTO protection_guide_version_counters (protection_type, next_version) VALUES
    ('INVENTION_PATENT', 1),
    ('UTILITY_MODEL', 1),
    ('INDUSTRIAL_DESIGN', 1),
    ('COPYRIGHT', 1),
    ('DISTINCTIVE_SIGN', 1),
    ('PLANT_BREEDER_CERTIFICATE', 1),
    ('TRADE_SECRET', 1);

CREATE TABLE protection_guides (
    guide_id UUID PRIMARY KEY,
    protection_type VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    short_description VARCHAR(240) NOT NULL,
    next_steps_content_html TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_source VARCHAR(16) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ,
    activated_by BIGINT,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    last_verified_at TIMESTAMPTZ,
    CONSTRAINT fk_protection_guides_counter FOREIGN KEY (protection_type)
        REFERENCES protection_guide_version_counters (protection_type),
    CONSTRAINT fk_protection_guides_created_by FOREIGN KEY (created_by) REFERENCES users (user_id),
    CONSTRAINT fk_protection_guides_updated_by FOREIGN KEY (updated_by) REFERENCES users (user_id),
    CONSTRAINT fk_protection_guides_activated_by FOREIGN KEY (activated_by) REFERENCES users (user_id),
    CONSTRAINT uk_protection_guides_type_version UNIQUE (protection_type, version),
    CONSTRAINT ck_protection_guides_type CHECK (protection_type IN (
        'INVENTION_PATENT', 'UTILITY_MODEL', 'INDUSTRIAL_DESIGN', 'COPYRIGHT',
        'DISTINCTIVE_SIGN', 'PLANT_BREEDER_CERTIFICATE', 'TRADE_SECRET'
    )),
    CONSTRAINT ck_protection_guides_version CHECK (version > 0),
    CONSTRAINT ck_protection_guides_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_protection_guides_source CHECK (created_source IN ('SYSTEM', 'ADMIN')),
    CONSTRAINT ck_protection_guides_short_description
        CHECK (char_length(btrim(short_description)) BETWEEN 30 AND 240),
    CONSTRAINT ck_protection_guides_content
        CHECK (char_length(btrim(next_steps_content_html)) BETWEEN 1 AND 50000),
    CONSTRAINT ck_protection_guides_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_protection_guides_source_actor CHECK (
        (created_source = 'SYSTEM' AND created_by IS NULL)
        OR (created_source = 'ADMIN' AND created_by IS NOT NULL)
    ),
    CONSTRAINT ck_protection_guides_activation CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND activated_by IS NULL
            AND retired_at IS NULL AND last_verified_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL
            AND retired_at IS NULL AND last_verified_at IS NOT NULL)
        OR (status = 'RETIRED' AND activated_at IS NOT NULL
            AND retired_at IS NOT NULL AND last_verified_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_protection_guides_single_active
    ON protection_guides (protection_type) WHERE status = 'ACTIVE';

CREATE INDEX idx_protection_guides_type_status
    ON protection_guides (protection_type, status);
