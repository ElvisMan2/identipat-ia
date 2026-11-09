package com.mnk.identipatia.analysis.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.analysis.admission")
public record AnalysisAdmissionProperties(
        boolean enabled,
        @Min(1) int standardMaxPerHour,
        @Min(1) int standardMaxPer24Hours,
        @Min(1) int standardAudioMaxPer24Hours,
        @Min(1) int standardMaxActive,
        @Min(1) int globalMaxActive,
        @Min(1) int audioMaxPreprocessing,
        @NotNull Duration reservationTtl,
        @NotNull Duration reservationSafetyMargin,
        @NotNull Duration retryAfter) {

    public AnalysisAdmissionProperties {
        requirePositive(reservationTtl, "reservationTtl");
        requirePositive(reservationSafetyMargin, "reservationSafetyMargin");
        requirePositive(retryAfter, "retryAfter");
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
