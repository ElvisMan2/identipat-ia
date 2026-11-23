package com.mnk.identipatia.ai.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.ai.enabled:false}' == 'true' && '${app.ai.provider:openai}' == 'gemini'")
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfiguration {

    @Bean
    InitializingBean geminiTimeoutLeaseGuard(
            GeminiProperties properties,
            Environment environment) {
        return () -> {
            Duration leaseDuration = Binder.get(environment)
                    .bind("app.analysis.lease-duration", Duration.class)
                    .orElse(Duration.ofSeconds(120));
            if (leaseDuration == null || !leaseDuration.minus(properties.timeout()).isPositive()) {
                throw new IllegalStateException("Analysis lease duration must exceed the Gemini timeout");
            }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GeminiTransport.class)
    GeminiTransport geminiTransport(GeminiProperties properties) {
        try {
            return new GoogleGenAiGeminiTransport(createClient(properties, null, null));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Gemini ADC client could not be initialized");
        }
    }

    @Bean
    GenerativeAiProvider geminiGenerativeAiProvider(
            GeminiTransport transport,
            GeminiProperties properties,
            ObjectMapper objectMapper,
            StructuredOutputValidator validator) {
        return new GeminiGenerativeAiProvider(
                transport, properties.model(), objectMapper, validator);
    }

    static Client createClient(
            GeminiProperties properties,
            GoogleCredentials credentials,
            String testBaseUrl) {
        HttpOptions httpOptions = httpOptions(properties, testBaseUrl);

        Client.Builder builder = Client.builder()
                .enterprise(true)
                .project(properties.project())
                .location(properties.location())
                .httpOptions(httpOptions);
        if (credentials != null) {
            builder.credentials(credentials);
        }
        return builder.build();
    }

    static HttpOptions httpOptions(GeminiProperties properties, String testBaseUrl) {
        HttpOptions.Builder http = HttpOptions.builder()
                .apiVersion(properties.apiVersion())
                .timeout(Math.toIntExact(properties.timeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1));
        if (testBaseUrl != null) {
            http.baseUrl(testBaseUrl);
        }
        return http.build();
    }
}
