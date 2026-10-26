CREATE SEQUENCE report_disclaimer_version_seq START WITH 2 INCREMENT BY 1;

CREATE TABLE report_disclaimers (
    disclaimer_id UUID PRIMARY KEY,
    version BIGINT NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_source VARCHAR(16) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ,
    activated_by BIGINT,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT fk_report_disclaimers_created_by FOREIGN KEY (created_by) REFERENCES users (user_id),
    CONSTRAINT fk_report_disclaimers_updated_by FOREIGN KEY (updated_by) REFERENCES users (user_id),
    CONSTRAINT fk_report_disclaimers_activated_by FOREIGN KEY (activated_by) REFERENCES users (user_id),
    CONSTRAINT ck_report_disclaimers_version CHECK (version > 0),
    CONSTRAINT ck_report_disclaimers_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT ck_report_disclaimers_content CHECK (char_length(btrim(content)) BETWEEN 1 AND 5000),
    CONSTRAINT ck_report_disclaimers_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_report_disclaimers_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_report_disclaimers_source CHECK (created_source IN ('SYSTEM', 'ADMIN')),
    CONSTRAINT ck_report_disclaimers_source_actor CHECK (
        (created_source = 'SYSTEM' AND created_by IS NULL)
        OR (created_source = 'ADMIN' AND created_by IS NOT NULL)
    ),
    CONSTRAINT ck_report_disclaimers_activation CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND activated_by IS NULL AND retired_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND activated_at IS NOT NULL AND retired_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_report_disclaimers_single_active
    ON report_disclaimers ((status)) WHERE status = 'ACTIVE';

INSERT INTO report_disclaimers (
    disclaimer_id, version, title, content, content_sha256, status, created_source,
    created_by, created_at, updated_by, updated_at, activated_by, activated_at, retired_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    1,
    'Importante',
    'Este reporte constituye una guía complementaria basada en inteligencia artificial y tiene carácter orientativo. No reemplaza, bajo ninguna circunstancia, el juicio técnico, la evaluación experta ni las decisiones oficiales del personal examinador del Indecopi. Sus resultados deben interpretarse bajo el principio de supervisión humana.',
    '4ec5ec3db0098b29667c1e3ba03359bfc1bc46542b0931dfb19937775e128967',
    'ACTIVE',
    'SYSTEM',
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    NULL
);

CREATE TABLE analysis_report_contexts (
    analysis_id UUID PRIMARY KEY,
    disclaimer_id UUID NOT NULL,
    report_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_analysis_report_contexts_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses (analysis_id),
    CONSTRAINT fk_analysis_report_contexts_disclaimer
        FOREIGN KEY (disclaimer_id) REFERENCES report_disclaimers (disclaimer_id),
    CONSTRAINT ck_analysis_report_contexts_version
        CHECK (report_version = 'diagnostic-report/1.0')
);

INSERT INTO analysis_report_contexts (analysis_id, disclaimer_id, report_version, created_at)
SELECT analysis_id,
       '00000000-0000-0000-0000-000000000001',
       'diagnostic-report/1.0',
       COALESCE(completed_at, updated_at)
FROM analyses
WHERE status = 'COMPLETED';

CREATE INDEX idx_analysis_report_contexts_disclaimer_id
    ON analysis_report_contexts (disclaimer_id);
