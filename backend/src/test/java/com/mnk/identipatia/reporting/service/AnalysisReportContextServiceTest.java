package com.mnk.identipatia.reporting.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisReportContextServiceTest {
    @Test
    void mapsCanonicalResultVersionsToTheirPresentationVersions() {
        assertThat(AnalysisReportContextService.reportVersionForResult("analysis-result/1.0"))
                .isEqualTo("diagnostic-report/1.0");
        assertThat(AnalysisReportContextService.reportVersionForResult("analysis-result/2.0"))
                .isEqualTo("diagnostic-report/1.0");
        assertThat(AnalysisReportContextService.reportVersionForResult("analysis-result/3.0"))
                .isEqualTo("diagnostic-report/1.1");
    }

    @Test
    void rejectsUnknownOrMissingCanonicalResultVersionInsteadOfFallingBack() {
        assertThatThrownBy(() -> AnalysisReportContextService.reportVersionForResult("analysis-result/9.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("analysis-result/9.0");
        assertThatThrownBy(() -> AnalysisReportContextService.reportVersionForResult(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported analysis result schema version");
    }
}
