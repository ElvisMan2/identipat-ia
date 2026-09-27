package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.dto.TextAnalysisRequest;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutputSchemaRegistry schemaRegistry = new OutputSchemaRegistry(objectMapper);
    private final StructuredOutputValidator validator = new StructuredOutputValidator();
    private final AnalysisResultReader reader = new AnalysisResultReader(objectMapper);

    @Test
    void requestToStringRedactsTheDescription() {
        assertThat(new TextAnalysisRequest("secreto técnico no debe aparecer").toString())
                .doesNotContain("secreto técnico no debe aparecer")
                .contains("<redacted>");
    }

    @Test
    void bothPromptVersionsLoadAndV02UsesOnlyUserDescriptionDeterministically() {
        PromptRegistry registry = new PromptRegistry();
        assertThat(registry.load(new PromptReference("intellectual-property-analysis", "0.1")))
                .isNotNull();
        assertThat(registry.load(new PromptReference("intellectual-property-analysis", "0.2")))
                .isNotNull();

        PromptRenderer renderer = new PromptRenderer(registry);
        var reference = new PromptReference("intellectual-property-analysis", "0.2");
        var first = renderer.render(reference, Map.of("USER_DESCRIPTION", "Descripción técnica sintética."));
        var second = renderer.render(reference, Map.of("USER_DESCRIPTION", "Descripción técnica sintética."));

        assertThat(first.renderedSnapshot()).contains("Descripción técnica sintética.")
                .doesNotContain("{{", "userId", "sessionId", "DNI", "email");
        assertThat(first.templateHash()).matches("[0-9a-f]{64}").isEqualTo(second.templateHash());
        assertThat(first.renderedHash()).matches("[0-9a-f]{64}").isEqualTo(second.renderedHash());
        assertThatThrownBy(() -> renderer.render(reference, Map.of(
                "USER_DESCRIPTION", "Descripción", "SYSTEM_ID", "internal")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unused");
    }

    @Test
    void bothSchemasLoadAndV20AcceptsCanonicalResult() throws Exception {
        assertThat(schemaRegistry.load("analysis-result", "1.0").jsonSchema()).isNotNull();
        var definition = schemaRegistry.load("analysis-result", "2.0");
        JsonNode valid = validV2();

        validator.validate(definition, valid, "fake");
        assertThat(reader.readCurrent(valid)).isInstanceOf(AnalysisResultV2.class);
    }

    @Test
    void strictSchemaV20RejectsAdditionalPropertiesInvalidEnumsLiteralsAndVersion() throws Exception {
        assertInvalidV2(with("confidence", "0.9"));
        JsonNode wrongVersion = validV2();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongVersion)
                .put("schemaVersion", "analysis-result/1.0");
        assertInvalidV2(wrongVersion);

        JsonNode invalidType = validV2();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidType.path("protectionOptions").get(0))
                .put("type", "OTHER");
        assertInvalidV2(invalidType);

        JsonNode invalidArticle15 = validV2();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidArticle15.path("patentScreening")
                .path("article15").path("matches").get(0)).put("literal", "g");
        assertInvalidV2(invalidArticle15);

        JsonNode invalidArticle20 = validV2();
        var article20 = (com.fasterxml.jackson.databind.node.ObjectNode) invalidArticle20
                .path("patentScreening").path("article20");
        article20.put("assessment", "POTENTIAL_MATCH");
        article20.withArray("matches").addObject().put("literal", "e").put("rationale", "Inválido");
        assertInvalidV2(invalidArticle20);
    }

    @Test
    void historicalV10IsReadAsItsOwnTypeAndUnknownOrMismatchedVersionsFail() throws Exception {
        JsonNode historical = objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"Resumen histórico",
                  "patentabilityAssessment":{"outcome":"INSUFFICIENT_INFORMATION","rationale":"Faltan datos."},
                  "protectionOptions":[],
                  "observations":[],
                  "warnings":[]
                }
                """);
        validator.validate(schemaRegistry.load("analysis-result", "1.0"), historical, "fake");
        assertThat(reader.read("analysis-result/1.0", historical)).isInstanceOf(AnalysisResultV1.class);
        assertThatThrownBy(() -> reader.read("analysis-result/2.0", historical))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mismatch");
        assertThatThrownBy(() -> reader.read("analysis-result/9.0",
                objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
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
                        "app.analysis.ai-max-output-tokens=6000")
                .run(context -> assertThat(context).hasFailed());
    }

    private JsonNode with(String field, String value) throws Exception {
        return ((com.fasterxml.jackson.databind.node.ObjectNode) validV2()).put(field, value);
    }

    private void assertInvalidV2(JsonNode json) {
        assertThatThrownBy(() -> validator.validate(schemaRegistry.load("analysis-result", "2.0"), json, "fake"))
                .isInstanceOf(GenerativeAiException.class);
    }

    private JsonNode validV2() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"El software expresado podría protegerse por derecho de autor.",
                  "protectionOptions":[{
                    "type":"COPYRIGHT",
                    "applicability":"LIKELY",
                    "protectedSubjectMatter":"El código fuente y su expresión",
                    "rationale":"La descripción incluye un programa de ordenador.",
                    "legalBasis":[{"instrument":"DECISION_351","articles":["1", "3", "4"]}]
                  }],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{"assessment":"POTENTIAL_MATCH","rationale":"Software como tal.",
                      "matches":[{"literal":"e","rationale":"La materia descrita es software."}]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"No hay indicios.","matches":[]}
                  },
                  "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No hay indicios.",
                    "missingInformation":[]},
                  "observations":[],
                  "warnings":[]
                }
                """);
    }
}
