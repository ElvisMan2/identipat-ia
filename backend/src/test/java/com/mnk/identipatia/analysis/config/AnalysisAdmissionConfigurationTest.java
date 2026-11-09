package com.mnk.identipatia.analysis.config;

import com.mnk.identipatia.preprocessing.config.PreprocessingServiceProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisAdmissionConfigurationTest {

    @Test
    void enabledAdmissionRequiresTtlAboveAudioTimeoutAndMargin() {
        var preprocessing = preprocessing(Duration.ofSeconds(300));

        assertThatThrownBy(() -> new AnalysisAdmissionConfiguration.AdmissionConfigurationValidator(
                admission(true, Duration.ofSeconds(360)), preprocessing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must exceed");
        assertThatCode(() -> new AnalysisAdmissionConfiguration.AdmissionConfigurationValidator(
                admission(true, Duration.ofSeconds(361)), preprocessing)).doesNotThrowAnyException();
    }

    @Test
    void disabledCompatibilityModeDoesNotApplyCrossTimeoutCheck() {
        assertThatCode(() -> new AnalysisAdmissionConfiguration.AdmissionConfigurationValidator(
                admission(false, Duration.ofSeconds(1)), preprocessing(Duration.ofSeconds(300))))
                .doesNotThrowAnyException();
    }

    private static AnalysisAdmissionProperties admission(boolean enabled, Duration ttl) {
        return new AnalysisAdmissionProperties(
                enabled, 3, 10, 3, 1, 8, 1, ttl, Duration.ofSeconds(60), Duration.ofSeconds(10));
    }

    private static PreprocessingServiceProperties preprocessing(Duration audioTimeout) {
        return new PreprocessingServiceProperties(
                URI.create("http://preprocessing.test"), Duration.ofSeconds(5),
                Duration.ofSeconds(15), audioTimeout);
    }
}
