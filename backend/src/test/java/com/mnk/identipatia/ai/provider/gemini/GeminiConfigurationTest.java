package com.mnk.identipatia.ai.provider.gemini;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiConfiguration;
import com.mnk.identipatia.ai.provider.openai.OpenAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    JacksonAutoConfiguration.class))
            .withUserConfiguration(AiConfiguration.class, OpenAiConfiguration.class, GeminiConfiguration.class)
            .withBean(GeminiTransport.class,
                    () -> (model, contents, config) -> null);

    @Test
    void enabledGeminiCreatesOnlyGeminiProviderWithoutOpenAiKeyOrRemoteCall() {
        enabledGemini().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GenerativeAiProvider.class);
            assertThat(context.getBean(GenerativeAiProvider.class).providerId()).isEqualTo("gemini");
            assertThat(context).doesNotHaveBean(com.openai.client.OpenAIClient.class);
        });
    }

    @Test
    void enabledGeminiDoesNotResolveInactiveOpenAiPlaceholders() {
        enabledGemini().withPropertyValues(
                "app.ai.openai.api-key=${F2_MISSING_OPENAI_KEY}",
                "app.ai.openai.model=${F2_MISSING_OPENAI_MODEL}",
                "app.ai.openai.timeout=${F2_MISSING_OPENAI_TIMEOUT}")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GenerativeAiProvider.class);
                    assertThat(context.getBean(GenerativeAiProvider.class).providerId()).isEqualTo("gemini");
                });
    }

    @Test
    void disabledAiDoesNotValidateGeminiOrCreateProvider() {
        contextRunner.withPropertyValues(
                "app.ai.enabled=false",
                "app.ai.provider=gemini")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GenerativeAiProvider.class);
                });
    }

    @Test
    void enabledGeminiRequiresProjectLocationModelAndPositiveTimeout() {
        assertInvalid("app.ai.gemini.project=");
        assertInvalid("app.ai.gemini.location=");
        assertInvalid("app.ai.gemini.model=");
        assertInvalid("app.ai.gemini.timeout=0s");
        assertInvalid("app.ai.gemini.api-version=v1beta1");
    }

    @Test
    void geminiTimeoutMustRemainBelowWorkerLease() {
        enabledGemini()
                .withPropertyValues(
                        "app.ai.gemini.timeout=120s",
                        "app.analysis.lease-duration=120s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sdkHttpOptionsPinV1TimeoutAndOneTotalAttempt() {
        GeminiProperties properties = new GeminiProperties(
                "project", "global", "model", "v1", Duration.ofSeconds(60));

        var options = GeminiConfiguration.httpOptions(properties, null);

        assertThat(options.apiVersion()).contains("v1");
        assertThat(options.timeout()).contains(60_000);
        assertThat(options.retryOptions()).isPresent();
        assertThat(options.retryOptions().orElseThrow().attempts()).contains(1);
        assertThat(options.baseUrl()).isEmpty();
    }

    @Test
    void sdkClientUsesEnterpriseVertexProjectAndLocationWithoutRemoteCall() {
        GeminiProperties properties = new GeminiProperties(
                "test-project", "global", "model", "v1", Duration.ofSeconds(60));
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(
                "test-token-not-secret", Date.from(Instant.now().plusSeconds(3600))));

        try (var client = GeminiConfiguration.createClient(properties, credentials, null)) {
            assertThat(client.enterprise()).isTrue();
            assertThat(client.vertexAI()).isTrue();
            assertThat(client.project()).isEqualTo("test-project");
            assertThat(client.location()).isEqualTo("global");
            assertThat(client.apiKey()).isNull();
        }
    }

    private void assertInvalid(String override) {
        enabledGemini().withPropertyValues(override)
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner enabledGemini() {
        return contextRunner.withPropertyValues(
                "app.ai.enabled=true",
                "app.ai.provider=gemini",
                "app.ai.gemini.project=test-project",
                "app.ai.gemini.location=global",
                "app.ai.gemini.model=test-model",
                "app.ai.gemini.api-version=v1",
                "app.ai.gemini.timeout=60s",
                "app.analysis.lease-duration=120s");
    }
}
