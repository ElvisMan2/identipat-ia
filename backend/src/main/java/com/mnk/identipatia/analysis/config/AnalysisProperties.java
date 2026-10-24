package com.mnk.identipatia.analysis.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(
        @Min(1) int textMinLength,
        @Min(1) int textMaxLength,
        boolean workerEnabled,
        @NotNull Duration pollInterval,
        @Min(1) int workerThreads,
        @Min(0) int workerQueueCapacity,
        @NotNull Duration leaseDuration,
        @Min(1) int maxAttempts,
        @NotNull Duration retryDelay,
        @Min(1) int aiMaxOutputTokens,
        @Valid @NotNull Pdf pdf,
        @Valid @NotNull Audio audio) {

    public AnalysisProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(retryDelay, "retryDelay");
        if (textMinLength > 0 && textMaxLength > 0 && textMinLength > textMaxLength) {
            throw new IllegalArgumentException("textMinLength must not exceed textMaxLength");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    public record Pdf(
            @Min(1) long maxFileSizeBytes,
            @Min(1) int maxPages) {
    }

    public record Audio(
            @Min(1) long maxFileSizeBytes,
            @Min(1) int maxDurationSeconds) {
    }
}
