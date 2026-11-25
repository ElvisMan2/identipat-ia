package com.mnk.identipatia.analysis.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisAttemptServicePromptVersionTest {

    @Test
    void newAttemptsUseGeneticResourcePromptsWithTheV3Schema() {
        assertThat(AnalysisAttemptService.TEXT_PROMPT.promptId()).isEqualTo("intellectual-property-analysis");
        assertThat(AnalysisAttemptService.TEXT_PROMPT.version()).isEqualTo("0.5.1");
        assertThat(AnalysisAttemptService.PDF_PROMPT.promptId()).isEqualTo("intellectual-property-analysis");
        assertThat(AnalysisAttemptService.PDF_PROMPT.version()).isEqualTo("0.5");
        assertThat(AnalysisAttemptService.SCHEMA_ID).isEqualTo("analysis-result");
        assertThat(AnalysisAttemptService.SCHEMA_VERSION).isEqualTo("3.0");
    }
}
