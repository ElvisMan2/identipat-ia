package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.ai.enabled:false}' == 'true' && '${app.ai.provider:openai}' == 'openai'")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {

    @Bean(destroyMethod = "close")
    OpenAIClient openAIClient(OpenAiProperties properties) {
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }

    @Bean
    GenerativeAiProvider openAiGenerativeAiProvider(
            OpenAIClient client,
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            StructuredOutputValidator validator) {
        return new OpenAiGenerativeAiProvider(client, properties.model(), objectMapper, validator);
    }
}
