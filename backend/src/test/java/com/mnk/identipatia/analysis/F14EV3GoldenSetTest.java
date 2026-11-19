package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class F14EV3GoldenSetTest {

    @Test
    void v3GoldenSetContainsFifteenStructuredScenariosWithoutChangingHistoricalFixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/f1.4e/legal-evaluation-cases-v3.json")) {
            assertThat(stream).isNotNull();
            JsonNode root = new ObjectMapper().readTree(stream);

            assertThat(root.path("schemaVersion").asText()).isEqualTo("f1.4e-golden-set/1.0");
            assertThat(root.path("resultSchemaVersion").asText()).isEqualTo("analysis-result/3.0");
            assertThat(root.path("cases").size()).isEqualTo(15);
            assertThat(root.path("cases").findValuesAsText("id"))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 15)
                            .mapToObj(number -> "V3-%02d".formatted(number)).toList());
            root.path("cases").forEach(testCase -> {
                assertThat(testCase.path("title").asText()).isNotBlank();
                assertThat(testCase.path("input").asText()).isNotBlank();
                assertStructuredSignals(testCase.path("positiveSignals"));
                assertStructuredSignals(testCase.path("negativeSignals"));
                assertThat(testCase.path("conditionalSignals").isArray()).isTrue();
                testCase.path("conditionalSignals").forEach(signal -> {
                    assertThat(signal.path("condition").asText()).isNotBlank();
                    assertThat(signal.path("assertion").asText()).isNotBlank();
                });
            });
        }
    }

    @Test
    void v3CasesCoverIndependentAccessIioPatentAndPhysicalPlausibilityRules() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/f1.4e/legal-evaluation-cases-v3.json")) {
            String fixture = new ObjectMapper().readTree(stream).toString();
            assertThat(fixture).contains(
                    "POTENTIAL_MATCH", "INVENTION_PATENT", "COPYRIGHT",
                    "efecto técnico físico externo", "efecto técnico interno al ordenador",
                    "movimiento perpetuo", "POTENTIALLY_REQUIRED", "INSUFFICIENT_INFORMATION",
                    "NOT_INDICATED", "intercambio tradicional", "dominio público",
                    "geneticResourceAccess", "traditionalKnowledgeAccess");
        }
    }

    private static void assertStructuredSignals(JsonNode signals) {
        assertThat(signals.isArray()).isTrue();
        assertThat(signals.isEmpty()).isFalse();
        signals.forEach(signal -> {
            assertThat(signal.path("path").asText()).isNotBlank();
            assertThat(signal.path("operator").asText()).isNotBlank();
            assertThat(signal.has("value")).isTrue();
            assertThat(signal.path("rationale").asText()).isNotBlank();
        });
    }
}
