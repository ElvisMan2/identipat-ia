package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class F14GoldenSetTest {

    @Test
    void goldenSetContainsJ01ThroughJ18WithMaintainableRuleCategories() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4/legal-evaluation-cases.json")) {
            assertThat(stream).isNotNull();
            JsonNode root = new ObjectMapper().readTree(stream);

            assertThat(root.path("schemaVersion").asText()).isEqualTo("f1.4-golden-set/1.0");
            assertThat(root.path("globalMustNotHave").isEmpty()).isFalse();
            assertThat(root.path("cases").size()).isEqualTo(18);
            assertThat(root.path("cases").findValuesAsText("id"))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 18)
                            .mapToObj(number -> "J%02d".formatted(number)).toList());
            root.path("cases").forEach(testCase -> {
                assertThat(testCase.path("title").asText()).isNotBlank();
                assertThat(testCase.path("input").asText()).isNotBlank();
                assertThat(testCase.path("mustHave").isEmpty()).isFalse();
                assertThat(testCase.path("mustNotHave").isEmpty()).isFalse();
                assertThat(testCase.path("conditional").isArray()).isTrue();
            });
        }
    }

    @Test
    void postLiveCasesDocumentTheTightenedSemanticRules() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4/legal-evaluation-cases.json")) {
            JsonNode cases = new ObjectMapper().readTree(stream).path("cases");

            assertThat(joinRules(assertCase(cases, "J01"))).contains("INVENTION_PATENT", "TRADE_SECRET",
                    "article15 literal c", "article15 literal d");
            assertThat(joinRules(assertCase(cases, "J03"))).contains("UTILITY_MODEL LIKELY",
                    "article15 NO_POTENTIAL_MATCH", "article20 NO_POTENTIAL_MATCH", "actividad inventiva");
            assertThat(joinRules(assertCase(cases, "J08"))).contains("geneticResourceAccess INSUFFICIENT_INFORMATION");
            assertThat(joinRules(assertCase(cases, "J09"))).contains("article15 NO_POTENTIAL_MATCH",
                    "article20 NO_POTENTIAL_MATCH");
            assertThat(joinRules(assertCase(cases, "J10"))).contains("COPYRIGHT", "TRADE_SECRET");
            assertThat(joinRules(assertCase(cases, "J14"))).contains("article15 INSUFFICIENT_INFORMATION",
                    "article15 matches vacío");
            assertThat(joinRules(assertCase(cases, "J16"))).contains("manuales inventados", "fichas técnicas inventadas",
                    "INVENTION_PATENT con DECISION_486 artículos 1, 15 o 20");
        }
    }

    private JsonNode assertCase(JsonNode cases, String id) {
        return java.util.stream.StreamSupport.stream(cases.spliterator(), false)
                .filter(testCase -> id.equals(testCase.path("id").asText()))
                .findFirst()
                .orElseThrow();
    }

    private String joinRules(JsonNode testCase) {
        StringBuilder rules = new StringBuilder();
        testCase.path("mustHave").forEach(rule -> rules.append(rule.asText()).append('\n'));
        testCase.path("mustNotHave").forEach(rule -> rules.append(rule.asText()).append('\n'));
        testCase.path("conditional").forEach(rule -> rules.append(rule.asText()).append('\n'));
        return rules.toString();
    }
}
