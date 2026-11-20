package com.mnk.identipatia.analysis.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisAttemptServicePromptVersionTest {

    @Test
    void newAttemptsUseCitizenLanguagePromptWithTheV3Schema() {
        assertThat(AnalysisAttemptService.PROMPT.promptId()).isEqualTo("intellectual-property-analysis");
        assertThat(AnalysisAttemptService.PROMPT.version()).isEqualTo("0.3.1");
        assertThat(AnalysisAttemptService.SCHEMA_ID).isEqualTo("analysis-result");
        assertThat(AnalysisAttemptService.SCHEMA_VERSION).isEqualTo("3.0");
    }
}
