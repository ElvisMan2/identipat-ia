package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.dto.TextAnalysisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutputSchemaRegistry schemaRegistry = new OutputSchemaRegistry(objectMapper);

    @Test
    void requestToStringRedactsTheDescription() {
        assertThat(new TextAnalysisRequest("secreto técnico no debe aparecer").toString())
                .doesNotContain("secreto técnico no debe aparecer")
                .contains("<redacted>");
    }

    @Test
    void promptV01UsesOnlyTheDescriptionVariableAndKeepsTraceabilityHashes() {
        PromptRenderer renderer = new PromptRenderer(new com.mnk.identipatia.ai.prompt.PromptRegistry());

        var rendered = renderer.render(
                new PromptReference("intellectual-property-analysis", "0.1"),
                Map.of("USER_DESCRIPTION", "Descripción de un mecanismo técnico."));

        assertThat(rendered.renderedSnapshot()).contains("Descripción de un mecanismo técnico.")
                .doesNotContain("userId", "sessionId", "DNI", "email");
        assertThat(rendered.templateHash()).matches("[0-9a-f]{64}");
        assertThat(rendered.renderedHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void strictSchemaV10AcceptsCanonicalResultAndRejectsUnknownProperties() throws Exception {
        var definition = schemaRegistry.load("analysis-result", "1.0");
        StructuredOutputValidator validator = new StructuredOutputValidator();
        JsonNode valid = objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"Resumen",
                  "patentabilityAssessment":{
                    "outcome":"INSUFFICIENT_INFORMATION",
                    "rationale":"Faltan detalles técnicos."
                  },
                  "protectionOptions":[],
                  "observations":[],
                  "warnings":[]
                }
                """);
        validator.validate(definition, valid, "fake");
        assertThat(objectMapper.treeToValue(valid, AnalysisResult.class).schemaVersion())
                .isEqualTo("analysis-result/1.0");

        ((com.fasterxml.jackson.databind.node.ObjectNode) valid).put("confidence", 0.9);
        assertThatThrownBy(() -> validator.validate(definition, valid, "fake"))
                .isInstanceOf(GenerativeAiException.class);
    }

    @Test
    void analysisConfigurationRejectsInvalidLimitsAndDurations() {
        new ApplicationContextRunner()
                .withUserConfiguration(com.mnk.identipatia.analysis.config.AnalysisWorkerConfiguration.class)
                .withPropertyValues(
                        "app.analysis.text-min-length=30",
                        "app.analysis.text-max-length=20",
                        "app.analysis.worker-enabled=false",
                        "app.analysis.poll-interval=2s",
                        "app.analysis.worker-threads=2",
                        "app.analysis.worker-queue-capacity=2",
                        "app.analysis.lease-duration=120s",
                        "app.analysis.max-attempts=2",
                        "app.analysis.retry-delay=5s",
                        "app.analysis.ai-max-output-tokens=4000")
                .run(context -> assertThat(context).hasFailed());
    }
}
