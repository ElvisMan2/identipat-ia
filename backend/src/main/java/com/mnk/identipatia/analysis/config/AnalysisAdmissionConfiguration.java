package com.mnk.identipatia.analysis.config;

import com.mnk.identipatia.preprocessing.config.PreprocessingServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnalysisAdmissionProperties.class)
public class AnalysisAdmissionConfiguration {

    @Bean
    AdmissionConfigurationValidator admissionConfigurationValidator(
            AnalysisAdmissionProperties admission,
            PreprocessingServiceProperties preprocessing) {
        return new AdmissionConfigurationValidator(admission, preprocessing);
    }

    static final class AdmissionConfigurationValidator {
        AdmissionConfigurationValidator(
                AnalysisAdmissionProperties admission,
                PreprocessingServiceProperties preprocessing) {
            if (admission.enabled()
                    && admission.reservationTtl().compareTo(
                            preprocessing.audioTimeout().plus(admission.reservationSafetyMargin())) <= 0) {
                throw new IllegalStateException(
                        "Analysis admission reservation TTL must exceed the audio timeout plus safety margin");
            }
        }
    }
}
