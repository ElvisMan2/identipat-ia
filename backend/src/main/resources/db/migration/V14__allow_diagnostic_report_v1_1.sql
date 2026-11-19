ALTER TABLE analysis_report_contexts
    DROP CONSTRAINT ck_analysis_report_contexts_version;

ALTER TABLE analysis_report_contexts
    ADD CONSTRAINT ck_analysis_report_contexts_version
    CHECK (report_version IN (
        'diagnostic-report/1.0',
        'diagnostic-report/1.1'
    ));
