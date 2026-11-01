CREATE TABLE guidance_views (
    guidance_view_id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    protection_type VARCHAR(40) NOT NULL,
    guide_version BIGINT NOT NULL,
    first_viewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_guidance_views_analysis FOREIGN KEY (analysis_id)
        REFERENCES analyses (analysis_id),
    CONSTRAINT fk_guidance_views_guide FOREIGN KEY (protection_type, guide_version)
        REFERENCES protection_guides (protection_type, version),
    CONSTRAINT uk_guidance_views_analysis_type_version
        UNIQUE (analysis_id, protection_type, guide_version),
    CONSTRAINT ck_guidance_views_version CHECK (guide_version > 0),
    CONSTRAINT ck_guidance_views_type CHECK (protection_type IN (
        'INVENTION_PATENT', 'UTILITY_MODEL', 'INDUSTRIAL_DESIGN', 'COPYRIGHT',
        'DISTINCTIVE_SIGN', 'PLANT_BREEDER_CERTIFICATE', 'TRADE_SECRET'
    ))
);

