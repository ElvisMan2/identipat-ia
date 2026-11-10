package com.mnk.identipatia.analysis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AnalysisIdempotencyProperties.class)
public class AnalysisIdempotencyConfiguration {

    @Bean
    IdempotencyConfigurationValidator idempotencyConfigurationValidator(
            AnalysisIdempotencyProperties idempotency,
            AnalysisAdmissionProperties admission) {
        return new IdempotencyConfigurationValidator(idempotency, admission);
    }

    static final class IdempotencyConfigurationValidator {
        IdempotencyConfigurationValidator(
                AnalysisIdempotencyProperties idempotency,
                AnalysisAdmissionProperties admission) {
            if (idempotency.claimTtl().compareTo(admission.reservationTtl()) <= 0) {
                throw new IllegalStateException(
                        "Analysis idempotency claim TTL must exceed the admission reservation TTL");
            }
            if (idempotency.retention().compareTo(idempotency.claimTtl()) <= 0) {
                throw new IllegalStateException(
                        "Analysis idempotency retention must exceed the claim TTL");
            }
            if (idempotency.retention().compareTo(Duration.ofHours(24)) <= 0) {
                throw new IllegalStateException(
                        "Analysis idempotency retention must exceed the 24 hour quota window");
            }
        }
    }
}
