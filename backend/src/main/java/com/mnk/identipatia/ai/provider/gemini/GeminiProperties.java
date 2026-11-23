package com.mnk.identipatia.ai.provider.gemini;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(
        @NotBlank String project,
        @NotBlank String location,
        @NotBlank String model,
        @NotBlank @Pattern(regexp = "v1") String apiVersion,
        @NotNull Duration timeout) {

    public GeminiProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be greater than zero");
        }
        if (timeout != null && timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout exceeds the SDK supported range");
        }
    }
}
