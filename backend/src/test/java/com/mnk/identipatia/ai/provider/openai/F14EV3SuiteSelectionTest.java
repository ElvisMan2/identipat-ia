package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class F14EV3SuiteSelectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void v2ContainsUnchangedV1CasesAndFourUniqueNewCases() throws Exception {
        JsonNode v1 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast");
        JsonNode v2 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast-v2");
        assertThat(v2).hasSize(8);
        for (int i = 0; i < 4; i++) assertThat(v2.get(i)).isEqualTo(v1.get(i));
        Set<String> ids = new HashSet<>();
        v2.forEach(testCase -> assertThat(ids.add(testCase.path("id").asText())).isTrue());
        assertThat(ids).containsExactlyInAnyOrder("QA-01", "QA-02", "QA-03", "QA-04",
                "QA-05", "QA-06", "QA-07", "QA-08");
        assertThat(OpenAiF14EV3LiveEvalIT.loadCases(mapper, "golden")).hasSize(15);
    }

    @Test
    void contrastV2SelectorRunsOnlyRequestedNewCases() throws Exception {
        JsonNode v1 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast");
        JsonNode v2 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast-v2");
        var selected = OpenAiF14EV3LiveEvalIT.selectCases(v2, "QA-05,QA-06,QA-07,QA-08");
        assertThat(selected).extracting(testCase -> testCase.path("id").asText())
                .containsExactly("QA-05", "QA-06", "QA-07", "QA-08");
        assertThatThrownBy(() -> OpenAiF14EV3LiveEvalIT.selectCases(v1, "QA-05"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void originalV1AndGoldenFixtureHashesRemainPinned() throws Exception {
        assertThat(resourceSha256("/f1.4e/contrast-cases-v1.json"))
                .isEqualTo("B31E7E18B2E18B4BB4DB4EFEE356A86F9DCE82F123F7EF90E04405584FAA9C1E");
        assertThat(resourceSha256("/f1.4e/legal-evaluation-cases-v3.json"))
                .isEqualTo("486EB728227713208DDD8620C9918B42437D90E4DF812D463171C84C4FB29132");
    }

    @Test
    void v2ExpectationPathsAndOperatorsUseTheImplementedFixtureGrammar() throws Exception {
        JsonNode v2 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast-v2");
        Set<String> supported = Set.of("exists", "equals", "in", "nonEmpty", "sameAs", "allowed",
                "absentBecauseOf", "asserts", "automatic", "considers", "containsConcept", "createdBy",
                "derivedFrom", "duplicates", "requires", "triggeredBy");
        Pattern segment = Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:\\[[A-Za-z][A-Za-z0-9]*=[A-Za-z0-9_]+\\])?$");
        for (JsonNode testCase : v2) {
            for (String group : new String[]{"positiveSignals", "negativeSignals"}) {
                for (JsonNode signal : testCase.path(group)) {
                    assertThat(supported).contains(signal.path("operator").asText());
                    for (String pathPart : signal.path("path").asText().split("\\.")) {
                        assertThat(segment.matcher(pathPart).matches())
                                .as("case %s path %s", testCase.path("id").asText(), signal.path("path").asText())
                                .isTrue();
                    }
                    assertThat(signal.has("value")).isTrue();
                }
            }
        }
    }

    @Test
    void selectedNewConditionalSignalsAreNotApplicableAndDoNotCountAsApprovals() throws Exception {
        JsonNode v2 = OpenAiF14EV3LiveEvalIT.loadCases(mapper, "contrast-v2");
        var selected = OpenAiF14EV3LiveEvalIT.selectCases(v2, "QA-05,QA-06,QA-07,QA-08");
        F14EV3ExpectationEvaluator evaluator = new F14EV3ExpectationEvaluator();
        for (JsonNode testCase : selected) {
            var conditions = evaluator.evaluateConditionalSignals(testCase);
            assertThat(conditions).hasSize(1);
            assertThat(conditions.getFirst().status())
                    .isEqualTo(F14EV3ExpectationEvaluator.Status.NOT_APPLICABLE);
        }
        assertThat(selected).hasSize(4);
    }

    private String resourceSha256(String resource) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
    }
}
