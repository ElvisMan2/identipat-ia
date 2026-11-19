package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class F14EV3ContrastSetTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void contrastBatteryHasFourIndependentAuditableCases() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4e/contrast-cases-v1.json")) {
            assertThat(stream).isNotNull();
            JsonNode fixture = mapper.readTree(stream);
            assertThat(fixture.path("independentOfGoldenSet").asBoolean()).isTrue();
            assertThat(fixture.path("cases")).hasSize(4);
            Set<String> ids = new HashSet<>();
            for (JsonNode testCase : fixture.path("cases")) {
                assertThat(ids.add(testCase.path("id").asText())).isTrue();
                assertThat(testCase.path("id").asText()).startsWith("QA-");
                assertThat(testCase.path("input").asText()).isNotBlank();
                assertThat(testCase.path("expectedBehavior").asText()).isNotBlank();
                assertThat(testCase.path("justification").asText()).isNotBlank();
                assertThat(testCase.path("positiveSignals").isArray()).isTrue();
                assertThat(testCase.path("negativeSignals").isArray()).isTrue();
                assertThat(testCase.path("conditionalSignals").isArray()).isTrue();
            }
            assertThat(ids).containsExactlyInAnyOrder("QA-01", "QA-02", "QA-03", "QA-04");
        }
    }

    @Test
    void minimalPairsHoldTheirLegalAxisConstantAndStateTheIntendedDelta() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4e/contrast-minimal-pairs-v1.json")) {
            assertThat(stream).isNotNull();
            JsonNode fixture = mapper.readTree(stream);
            assertThat(fixture.path("independentOfGoldenAndFirstContrast").asBoolean()).isTrue();
            assertThat(fixture.path("pairs")).hasSize(2);
            Set<String> ids = new HashSet<>();
            for (JsonNode pair : fixture.path("pairs")) {
                assertThat(pair.path("axis").asText()).isNotBlank();
                assertThat(pair.path("cases")).hasSize(2);
                for (JsonNode testCase : pair.path("cases")) {
                    assertThat(ids.add(testCase.path("id").asText())).isTrue();
                    assertThat(testCase.path("input").asText()).isNotBlank();
                    assertThat(testCase.path("deltaFromPairBaseline").asText()).isNotBlank();
                    assertThat(testCase.path("expectedBehavior").asText()).isNotBlank();
                    assertThat(testCase.path("justification").asText()).isNotBlank();
                }
            }
            assertThat(ids).containsExactlyInAnyOrder("QA-05", "QA-06", "QA-07", "QA-08");
        }
    }
}
