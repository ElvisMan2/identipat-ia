package com.mnk.identipatia.ai.provider.openai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.openai")
public record OpenAiProperties(
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotNull Duration timeout) {

    public OpenAiProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be greater than zero");
        }
    }
}
