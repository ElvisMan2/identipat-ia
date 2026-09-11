package com.mnk.identipatia.preprocessing.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.preprocessing")
public record PreprocessingServiceProperties(
        @NotNull URI baseUrl,
        @NotNull Duration timeout) {
}
