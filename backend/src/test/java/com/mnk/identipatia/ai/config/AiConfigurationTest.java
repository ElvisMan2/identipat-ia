package com.mnk.identipatia.ai.config;

import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.provider.openai.OpenAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    JacksonAutoConfiguration.class))
            .withUserConfiguration(AiConfiguration.class, OpenAiConfiguration.class);

    @Test
    void disabledAiStartsWithoutOpenAiCredentialsOrProviderBean() {
        contextRunner.withPropertyValues(
                "app.ai.enabled=false",
                "app.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GenerativeAiProvider.class);
                });
    }

    @Test
    void enabledOpenAiRequiresApiKey() {
        enabledContext(
                "app.ai.openai.model=test-model",
                "app.ai.openai.timeout=60s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledOpenAiRequiresModel() {
        enabledContext(
                "app.ai.openai.api-key=test-key",
                "app.ai.openai.timeout=60s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledOpenAiRejectsInvalidTimeout() {
        enabledContext(
                "app.ai.openai.api-key=test-key",
                "app.ai.openai.model=test-model",
                "app.ai.openai.timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledAiRejectsUnknownProvider() {
        contextRunner.withPropertyValues(
                "app.ai.enabled=true",
                "app.ai.provider=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledOpenAiCreatesProviderWithoutCallingRemoteService() {
        enabledContext(
                "app.ai.openai.api-key=test-key",
                "app.ai.openai.model=test-model",
                "app.ai.openai.timeout=60s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GenerativeAiProvider.class);
                });
    }

    private ApplicationContextRunner enabledContext(String... properties) {
        return contextRunner
                .withPropertyValues("app.ai.enabled=true", "app.ai.provider=openai")
                .withPropertyValues(properties);
    }
}
