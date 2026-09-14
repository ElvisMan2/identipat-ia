package com.mnk.identipatia.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    InitializingBean aiConfigurationGuard(AiProperties properties) {
        return () -> {
            if (properties.enabled()
                    && (properties.provider() == null
                    || !"openai".equals(properties.provider()))) {
                throw new IllegalStateException("Unsupported generative AI provider");
            }
        };
    }

    @Bean
    PromptRegistry promptRegistry() {
        return new PromptRegistry();
    }

    @Bean
    PromptRenderer promptRenderer(PromptRegistry registry) {
        return new PromptRenderer(registry);
    }

    @Bean
    OutputSchemaRegistry outputSchemaRegistry(ObjectMapper objectMapper) {
        return new OutputSchemaRegistry(objectMapper);
    }

    @Bean
    StructuredOutputValidator structuredOutputValidator() {
        return new StructuredOutputValidator();
    }
}
