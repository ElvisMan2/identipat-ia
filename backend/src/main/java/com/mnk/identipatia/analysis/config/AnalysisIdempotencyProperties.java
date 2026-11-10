package com.mnk.identipatia.analysis.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.analysis.idempotency")
public record AnalysisIdempotencyProperties(
        boolean required,
        @Min(36) int maxKeyLength,
        @NotNull Duration claimTtl,
        @NotNull Duration retention,
        @NotNull Duration inProgressRetryAfter,
        @NotBlank @Size(min = 32) String hmacSecret) {

    public AnalysisIdempotencyProperties {
        requirePositive(claimTtl, "claimTtl");
        requirePositive(retention, "retention");
        requirePositive(inProgressRetryAfter, "inProgressRetryAfter");
        if (hmacSecret != null && hmacSecret.matches("\\$\\{[^}]+}")) {
            throw new IllegalArgumentException(
                    "hmacSecret must be supplied and cannot be an unresolved placeholder");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
