package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenAiLiveSmokeIT {

    @Test
    void callsOpenAiWithSyntheticStructuredSmokePayload() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank(),
                "OPENAI_API_KEY and OPENAI_MODEL are required for the manual live smoke test");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PromptRenderer promptRenderer = new PromptRenderer(new PromptRegistry());
        OutputSchemaRegistry schemaRegistry = new OutputSchemaRegistry(objectMapper);
        Duration timeout = parseTimeout(System.getenv("OPENAI_TIMEOUT"));

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(0)
                .build();
        try {
            OpenAiGenerativeAiProvider provider = new OpenAiGenerativeAiProvider(
                    client, model, objectMapper, new StructuredOutputValidator());
            GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                    promptRenderer.render(
                            new PromptReference("provider-smoke-test", "1.0"),
                            Map.of("TEST_LABEL", "manual-live-smoke")),
                    schemaRegistry.load("provider-smoke-result", "1.0"),
                    new GenerationOptions(200, null)));

            assertThat(response.structuredOutput().path("schemaVersion").asText())
                    .isEqualTo("provider-smoke-result/1.0");
            assertThat(response.structuredOutput().path("status").asText()).isEqualTo("OK");
            assertThat(response.provider().provider()).isEqualTo("openai");
            assertThat(response.provider().model()).isNotBlank();
            assertThat(response.provider().model())
                    .isEqualTo(response.rawProviderResponse().path("model").asText());
        } finally {
            client.close();
        }
    }

    private Duration parseTimeout(String configured) {
        return configured == null || configured.isBlank()
                ? Duration.ofSeconds(60)
                : DurationStyle.detectAndParse(configured);
    }
}
