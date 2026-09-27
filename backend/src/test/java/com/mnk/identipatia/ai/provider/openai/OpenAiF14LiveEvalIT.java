package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.Article15Literal;
import com.mnk.identipatia.analysis.result.v2.Article20Literal;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAssessment;
import com.mnk.identipatia.analysis.result.v2.LegalInstrument;
import com.mnk.identipatia.analysis.result.v2.ProtectionApplicability;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.analysis.result.v2.ScreeningAssessment;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Manual, opt-in prompt evaluation. It is not selected by the normal Surefire naming patterns. */
class OpenAiF14LiveEvalIT {

    static final int DEFAULT_MAX_OUTPUT_TOKENS = 6000;

    @Test
    void evaluatesSelectedF14CasesAgainstOpenAi() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("RUN_F14_LIVE_EVAL")),
                "Set RUN_F14_LIVE_EVAL=true to enable the manual evaluation");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank(),
                "OPENAI_API_KEY and OPENAI_MODEL are required");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode cases = loadCases(objectMapper);
        Set<String> selected = selectedCases();
        PromptRenderer renderer = new PromptRenderer(new PromptRegistry());
        OutputSchemaRegistry schemas = new OutputSchemaRegistry(objectMapper);
        StructuredOutputValidator validator = new StructuredOutputValidator();
        AnalysisResultReader reader = new AnalysisResultReader(objectMapper);
        int maxOutputTokens = parseMaxOutputTokens(System.getenv("F14_LIVE_EVAL_MAX_OUTPUT_TOKENS"));
        Path outputDirectory = Path.of(System.getProperty("basedir", "."), "target", "f14-live-eval");

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(parseTimeout(System.getenv("OPENAI_TIMEOUT")))
                .maxRetries(0)
                .build();
        try {
            OpenAiGenerativeAiProvider provider = new OpenAiGenerativeAiProvider(
                    client, model, objectMapper, validator);
            for (JsonNode testCase : cases) {
                String id = testCase.path("id").asText();
                if (!selected.contains(id)) {
                    continue;
                }
                long started = System.nanoTime();
                GenerativeAiResponse response = null;
                boolean passed = false;
                try {
                    response = provider.generate(new GenerativeAiRequest(
                            renderer.render(new PromptReference("intellectual-property-analysis", "0.2"),
                                    Map.of("USER_DESCRIPTION", testCase.path("input").asText())),
                            schemas.load("analysis-result", "2.0"),
                            new GenerationOptions(maxOutputTokens, null)));
                    AnalysisResultV2 result = reader.readCurrent(response.structuredOutput());
                    saveStructuredResponse(objectMapper, outputDirectory, id, response.structuredOutput());
                    validateDeterministicRules(id, result);
                    passed = true;
                } finally {
                    long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    System.out.printf(
                            "F1.4 live case=%s durationMs=%d model=%s inputTokens=%s outputTokens=%s pass=%s%n",
                            id, durationMs,
                            response == null ? "unavailable" : response.provider().model(),
                            response == null ? "unavailable" : response.tokenUsage().inputTokens(),
                            response == null ? "unavailable" : response.tokenUsage().outputTokens(),
                            passed);
                }
            }
        } finally {
            client.close();
        }
    }

    private JsonNode loadCases(ObjectMapper objectMapper) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4/legal-evaluation-cases.json")) {
            if (stream == null) {
                throw new IllegalStateException("F1.4 golden set is missing");
            }
            return objectMapper.readTree(stream).path("cases");
        }
    }

    private Set<String> selectedCases() {
        String configured = System.getenv("F14_LIVE_EVAL_CASES");
        if (configured == null || configured.isBlank()) {
            return Set.of("J01");
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateDeterministicRules(String id, AnalysisResultV2 result) {
        assertThat(result.schemaVersion()).isEqualTo(AnalysisResultV2.SCHEMA_VERSION);
        switch (id) {
            case "J01" -> {
                assertProtection(result, ProtectionType.COPYRIGHT, ProtectionApplicability.LIKELY,
                        ProtectionApplicability.POSSIBLE);
                assertArticle15(result, Article15Literal.e);
                assertThat(result.patentScreening().article15().matches()).extracting(match -> match.literal())
                        .containsOnly(Article15Literal.e);
                assertNoProtection(result, ProtectionType.INVENTION_PATENT, ProtectionType.TRADE_SECRET);
                assertNoInventedSubjects(result, "manuales", "ilustraciones", "datos de entrenamiento", "optimización");
            }
            case "J02" -> {
                assertProtection(result, ProtectionType.TRADE_SECRET);
                assertArticle15(result, Article15Literal.d);
            }
            case "J03" -> {
                assertProtection(result, ProtectionType.UTILITY_MODEL, ProtectionApplicability.LIKELY);
                assertThat(result.patentScreening().applicable()).isTrue();
                assertScreening(result.patentScreening().article15().assessment(),
                        ScreeningAssessment.NO_POTENTIAL_MATCH);
                assertScreening(result.patentScreening().article20().assessment(),
                        ScreeningAssessment.NO_POTENTIAL_MATCH);
                assertNotApplicableAs(result, ProtectionType.INVENTION_PATENT, ProtectionApplicability.LIKELY);
                assertInventionPatentLegalBasis(result);
                assertNoForbiddenTerms(result, "novedad", "nivel inventivo", "actividad inventiva",
                        "aplicación industrial", "ventaja técnica");
            }
            case "J04" -> assertProtection(result, ProtectionType.INVENTION_PATENT);
            case "J05" -> assertProtection(result, ProtectionType.INDUSTRIAL_DESIGN);
            case "J06" -> assertProtection(result, ProtectionType.DISTINCTIVE_SIGN);
            case "J07", "J17" -> assertProtection(result, ProtectionType.COPYRIGHT);
            case "J08" -> {
                assertProtection(result, ProtectionType.PLANT_BREEDER_CERTIFICATE, ProtectionApplicability.LIKELY);
                assertArticle20(result, Article20Literal.c);
                assertThat(result.geneticResourceAccess().assessment())
                        .isEqualTo(GeneticResourceAssessment.INSUFFICIENT_INFORMATION);
                assertThat(result.geneticResourceAccess().missingInformation()).isNotEmpty();
                assertNoProtection(result, ProtectionType.DISTINCTIVE_SIGN, ProtectionType.TRADE_SECRET);
            }
            case "J09" -> {
                assertThat(result.geneticResourceAccess().assessment())
                        .isEqualTo(GeneticResourceAssessment.POTENTIALLY_REQUIRED);
                assertThat(result.patentScreening().applicable()).isTrue();
                assertScreening(
                        result.patentScreening().article15().assessment(),
                        ScreeningAssessment.NO_POTENTIAL_MATCH);
                assertThat(result.patentScreening().article15().matches())
                        .isEmpty();
                assertScreening(
                        result.patentScreening().article20().assessment(),
                        ScreeningAssessment.NO_POTENTIAL_MATCH);
                assertThat(result.patentScreening().article20().matches())
                        .isEmpty();
                assertNoProtection(
                        result,
                        ProtectionType.COPYRIGHT,
                        ProtectionType.PLANT_BREEDER_CERTIFICATE,
                        ProtectionType.TRADE_SECRET);
                assertInventionPatentLegalBasis(result);
            }
            case "J10" -> {
                assertArticle20(result, Article20Literal.d);
                assertNoProtection(result, ProtectionType.COPYRIGHT, ProtectionType.TRADE_SECRET);
                assertNoForbiddenTerms(result, "novedad", "nivel inventivo", "aplicación industrial");
            }
            case "J18" -> assertArticle20(result, Article20Literal.d);
            case "J11" -> {
                assertThat(result.patentScreening().article15().assessment())
                        .isEqualTo(ScreeningAssessment.POTENTIAL_MATCH);
                assertThat(result.patentScreening().article15().matches())
                        .extracting(match -> match.literal()).containsAnyOf(Article15Literal.a, Article15Literal.b);
            }
            case "J12" -> assertProtection(result, ProtectionType.TRADE_SECRET);
            case "J13", "J15" -> assertThat(result.observations()).isNotEmpty();
            case "J14" -> {
                assertThat(result.geneticResourceAccess().assessment())
                        .isEqualTo(GeneticResourceAssessment.INSUFFICIENT_INFORMATION);
                assertThat(result.geneticResourceAccess().missingInformation()).isNotEmpty();
                assertScreening(result.patentScreening().article15().assessment(),
                        ScreeningAssessment.INSUFFICIENT_INFORMATION);
                assertThat(result.patentScreening().article15().matches()).isEmpty();
                assertNoProtection(result, ProtectionType.TRADE_SECRET);
            }
            case "J16" -> {
                assertProtection(result, ProtectionType.UTILITY_MODEL, ProtectionApplicability.LIKELY,
                        ProtectionApplicability.POSSIBLE);
                assertProtection(result, ProtectionType.INDUSTRIAL_DESIGN, ProtectionApplicability.LIKELY,
                        ProtectionApplicability.POSSIBLE);
                assertProtection(result, ProtectionType.DISTINCTIVE_SIGN, ProtectionApplicability.LIKELY,
                        ProtectionApplicability.POSSIBLE);
                assertProtection(result, ProtectionType.TRADE_SECRET, ProtectionApplicability.LIKELY,
                        ProtectionApplicability.POSSIBLE);
                assertNotApplicableAs(result, ProtectionType.INVENTION_PATENT, ProtectionApplicability.LIKELY);
                assertNotApplicableAs(result, ProtectionType.COPYRIGHT, ProtectionApplicability.LIKELY);
                assertInventionPatentLegalBasis(result);
                assertNoInventedSubjects(result, "manuales", "fichas técnicas");
                assertNoForbiddenTerms(result, "confirmar la originalidad", "evaluar la originalidad");
            }
            default -> throw new IllegalArgumentException("Unknown F1.4 case: " + id);
        }
    }

    private void assertProtection(
            AnalysisResultV2 result,
            ProtectionType type,
            ProtectionApplicability... allowedApplicability) {
        assertThat(result.protectionOptions()).anySatisfy(option -> {
            assertThat(option.type()).isEqualTo(type);
            assertThat(option.applicability()).isIn((Object[]) allowedApplicability);
        });
    }

    private void assertProtection(AnalysisResultV2 result, ProtectionType type) {
        assertThat(result.protectionOptions()).extracting(option -> option.type()).contains(type);
    }

    private void assertNoProtection(AnalysisResultV2 result, ProtectionType... types) {
        assertThat(result.protectionOptions()).extracting(option -> option.type()).doesNotContain(types);
    }

    private void assertNotApplicableAs(
            AnalysisResultV2 result,
            ProtectionType type,
            ProtectionApplicability applicability) {
        assertThat(result.protectionOptions()).noneSatisfy(option -> {
            assertThat(option.type()).isEqualTo(type);
            assertThat(option.applicability()).isEqualTo(applicability);
        });
    }

    private void assertInventionPatentLegalBasis(AnalysisResultV2 result) {
        result.protectionOptions().stream()
                .filter(option -> option.type() == ProtectionType.INVENTION_PATENT)
                .forEach(option -> {
                    assertThat(option.legalBasis()).anySatisfy(basis -> {
                        assertThat(basis.instrument()).isEqualTo(LegalInstrument.DECISION_486);
                        assertThat(basis.articles()).contains("14");
                    });
                    assertThat(option.legalBasis().stream()
                            .filter(basis -> basis.instrument() == LegalInstrument.DECISION_486)
                            .flatMap(basis -> basis.articles().stream()))
                            .doesNotContain("1", "15", "20");
                });
    }

    private void assertNoInventedSubjects(AnalysisResultV2 result, String... forbiddenSubjects) {
        String subjects = result.protectionOptions().stream()
                .map(option -> option.protectedSubjectMatter().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("\n"));
        for (String forbiddenSubject : forbiddenSubjects) {
            assertThat(subjects).doesNotContain(forbiddenSubject.toLowerCase(Locale.ROOT));
        }
    }

    private void assertNoForbiddenTerms(AnalysisResultV2 result, String... forbiddenTerms) {
        String output = resultText(result).toLowerCase(Locale.ROOT);
        for (String forbiddenTerm : forbiddenTerms) {
            assertThat(output).doesNotContain(forbiddenTerm.toLowerCase(Locale.ROOT));
        }
    }

    private String resultText(AnalysisResultV2 result) {
        List<String> text = new java.util.ArrayList<>();
        text.add(result.summary());
        text.addAll(result.observations());
        text.addAll(result.warnings());
        text.add(result.patentScreening().article15().rationale());
        text.add(result.patentScreening().article20().rationale());
        text.add(result.geneticResourceAccess().rationale());
        text.addAll(result.geneticResourceAccess().missingInformation());
        result.protectionOptions().forEach(option -> {
            text.add(option.protectedSubjectMatter());
            text.add(option.rationale());
        });
        result.patentScreening().article15().matches().forEach(match -> text.add(match.rationale()));
        result.patentScreening().article20().matches().forEach(match -> text.add(match.rationale()));
        return String.join("\n", text);
    }

    private void assertScreening(ScreeningAssessment actual, ScreeningAssessment expected) {
        assertThat(actual).isEqualTo(expected);
    }

    private void assertArticle15(AnalysisResultV2 result, Article15Literal literal) {
        assertThat(result.patentScreening().applicable()).isTrue();
        assertThat(result.patentScreening().article15().assessment())
                .isEqualTo(ScreeningAssessment.POTENTIAL_MATCH);
        assertThat(result.patentScreening().article15().matches()).extracting(match -> match.literal())
                .contains(literal);
    }

    private void assertArticle20(AnalysisResultV2 result, Article20Literal literal) {
        assertThat(result.patentScreening().applicable()).isTrue();
        assertThat(result.patentScreening().article20().assessment())
                .isEqualTo(ScreeningAssessment.POTENTIAL_MATCH);
        assertThat(result.patentScreening().article20().matches()).extracting(match -> match.literal())
                .contains(literal);
    }

    private Duration parseTimeout(String configured) {
        return configured == null || configured.isBlank()
                ? Duration.ofSeconds(60)
                : DurationStyle.detectAndParse(configured);
    }

    static int parseMaxOutputTokens(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_OUTPUT_TOKENS;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            if (value <= 0) {
                throw new IllegalArgumentException("F14_LIVE_EVAL_MAX_OUTPUT_TOKENS must be greater than zero");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "F14_LIVE_EVAL_MAX_OUTPUT_TOKENS must be a positive integer", exception);
        }
    }

    static void saveStructuredResponse(
            ObjectMapper objectMapper,
            Path outputDirectory,
            String caseId,
            JsonNode structuredOutput) throws Exception {
        if (caseId == null || !caseId.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid F1.4 case id: " + caseId);
        }
        Files.createDirectories(outputDirectory);
        String formattedJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(structuredOutput) + System.lineSeparator();
        Files.writeString(outputDirectory.resolve(caseId + ".json"), formattedJson, StandardCharsets.UTF_8);
    }
}
