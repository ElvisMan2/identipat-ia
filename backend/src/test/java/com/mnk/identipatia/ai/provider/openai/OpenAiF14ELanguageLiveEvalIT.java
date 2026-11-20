package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Independent opt-in language check for prompt 0.3.1. Each case makes one provider call. */
class OpenAiF14ELanguageLiveEvalIT {
    private static final int MAX_OUTPUT_TOKENS = 6000;
    private static final String PROMPT_ID = "intellectual-property-analysis";
    private static final String PROMPT_VERSION = "0.3.1";
    private static final String SCHEMA_ID = "analysis-result";
    private static final String SCHEMA_VERSION = "3.0";
    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final List<EvaluationCase> CASES = List.of(
            new EvaluationCase("LANG-01", "Tratamiento del artículo 15(e)",
                    "He desarrollado un programa de ordenador que recibe una lista de nombres y los ordena "
                            + "alfabéticamente mediante reglas de comparación. La solución consiste únicamente "
                            + "en el programa y sus reglas de funcionamiento. No se describe un mecanismo técnico "
                            + "adicional ni una mejora del funcionamiento interno del ordenador."),
            new EvaluationCase("LANG-02", "Tratamiento del artículo 20(d)",
                    "He desarrollado un procedimiento de tratamiento terapéutico para pacientes con una afección "
                            + "muscular. Consiste en aplicar directamente sobre el cuerpo del paciente una secuencia "
                            + "específica de maniobras manuales durante sesiones clínicas. Mi creación es el método "
                            + "de tratamiento en sí mismo, no un dispositivo ni un producto médico."));

    @Test
    void optInIsDisabledUnlessExplicitlyEnabled() {
        assertFalse(isEnabled(null));
        assertFalse(isEnabled(""));
        assertFalse(isEnabled("false"));
        assertTrue(isEnabled("true"));
    }

    @Test
    void evaluatesTwoLanguageCasesAgainstOpenAi() throws Exception {
        assumeTrue(isEnabled(System.getenv("RUN_F14E_LANGUAGE_LIVE_EVAL")),
                "Set RUN_F14E_LANGUAGE_LIVE_EVAL=true to enable the two-call manual evaluation");

        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY and OPENAI_MODEL are required when opt-in is enabled");
        }
        if (!"gpt-5-mini".equals(model)) {
            throw new IllegalStateException("OPENAI_MODEL must be gpt-5-mini for this evaluation");
        }

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Duration timeout = timeout(System.getenv("OPENAI_TIMEOUT"));
        String runId = RUN_STAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path runDirectory = Path.of("target", "f1.4e-language-live-eval", runId).toAbsolutePath();
        Files.createDirectories(runDirectory.getParent());
        Files.createDirectory(runDirectory);

        PromptRenderer promptRenderer = new PromptRenderer(new PromptRegistry());
        OutputSchemaRegistry schemaRegistry = new OutputSchemaRegistry(objectMapper);
        AnalysisResultReader resultReader = new AnalysisResultReader(objectMapper);
        StructuredOutputValidator schemaValidator = new StructuredOutputValidator();
        ObjectNode metadata = initialMetadata(objectMapper, runId, model, timeout, runDirectory);
        Map<String, ObjectNode> caseResults = new java.util.LinkedHashMap<>();
        AtomicInteger providerCalls = new AtomicInteger();
        writeArtifacts(objectMapper, runDirectory, metadata, caseResults);

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(0)
                .build();
        try {
            for (EvaluationCase evaluationCase : CASES) {
                ObjectNode result = initialCaseResult(objectMapper, evaluationCase);
                caseResults.put(evaluationCase.id(), result);
                Path inputFile = runDirectory.resolve(evaluationCase.id() + "-input.json");
                Path responseFile = runDirectory.resolve(evaluationCase.id() + "-response.json");
                writeInput(objectMapper, inputFile, evaluationCase);
                writeArtifacts(objectMapper, runDirectory, metadata, caseResults);

                var renderedPrompt = promptRenderer.render(
                        new PromptReference(PROMPT_ID, PROMPT_VERSION),
                        Map.of("USER_DESCRIPTION", evaluationCase.description()));
                StructuredOutputDefinition outputDefinition = schemaRegistry.load(SCHEMA_ID, SCHEMA_VERSION);
                result.put("promptId", renderedPrompt.reference().promptId());
                result.put("promptVersion", renderedPrompt.reference().version());
                result.put("promptTemplateSha256", renderedPrompt.templateHash());
                result.put("renderedPromptSha256", renderedPrompt.renderedHash());
                result.put("schemaId", outputDefinition.schemaId());
                result.put("schemaVersion", outputDefinition.schemaVersion());

                AtomicReference<String> captureError = new AtomicReference<>();
                StructuredOutputValidator capturingValidator = new StructuredOutputValidator() {
                    @Override
                    public void validate(StructuredOutputDefinition definition, JsonNode output, String provider) {
                        result.put("jsonValid", true);
                        try {
                            writeJsonNew(objectMapper, responseFile, output);
                            result.put("responseFile", responseFile.getFileName().toString());
                        } catch (Exception exception) {
                            captureError.set(exception.getClass().getName());
                            result.put("responseCaptureErrorType", exception.getClass().getName());
                        }
                        try {
                            schemaValidator.validate(definition, output, provider);
                            result.put("schemaValid", true);
                        } catch (RuntimeException exception) {
                            result.put("schemaValid", false);
                            throw exception;
                        }
                        if (captureError.get() != null) {
                            throw new IllegalStateException("Could not persist structured response");
                        }
                    }
                };

                long startedNanos = System.nanoTime();
                providerCalls.incrementAndGet();
                result.put("providerCallNumber", providerCalls.get());
                metadata.put("providerGenerateCalls", providerCalls.get());
                try {
                    OpenAiGenerativeAiProvider provider = new OpenAiGenerativeAiProvider(
                            client, model, objectMapper, capturingValidator);
                    GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                            renderedPrompt, outputDefinition, new GenerationOptions(MAX_OUTPUT_TOKENS, null)));
                    JsonNode structuredOutput = response.structuredOutput();
                    result.put("jsonValid", true);
                    result.put("schemaValid", true);
                    result.put("provider", response.provider().provider());
                    result.put("model", response.provider().model());
                    result.put("durationMs", response.latencyMs());
                    result.put("elapsedMs", Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
                    result.put("finishReason", response.finishReason());
                    addTokenUsage(result, response);

                    try {
                        AnalysisResultV3 domainResult = resultReader.readCurrent(structuredOutput);
                        result.put("domainValid", true);
                        result.put("domainSchemaVersion", domainResult.schemaVersion());
                    } catch (Exception exception) {
                        result.put("domainValid", false);
                        result.put("domainErrorType", exception.getClass().getName());
                    }
                    addNarrativeChecks(result, structuredOutput);
                    addLegalEvidence(result, structuredOutput);
                    result.set("manualReview", manualReviewChecklist(objectMapper));
                    result.put("caseStatus", result.path("domainValid").asBoolean(false)
                            && result.path("noScreeningInNarrative").asBoolean(false)
                            ? "READY_FOR_MANUAL_REVIEW" : "TECHNICAL_ERROR");
                } catch (Exception | AssertionError exception) {
                    result.put("caseStatus", "TECHNICAL_ERROR");
                    result.put("errorType", exception.getClass().getName());
                    result.put("elapsedMs", Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
                }
                writeArtifacts(objectMapper, runDirectory, metadata, caseResults);
            }
        } finally {
            client.close();
        }

        metadata.put("providerGenerateCalls", providerCalls.get());
        metadata.put("completedAt", Instant.now().toString());
        metadata.put("runDurationMs", metadata.path("startedAtNanos").asLong() == 0
                ? 0 : Duration.ofNanos(System.nanoTime() - metadata.path("startedAtNanos").asLong()).toMillis());
        metadata.remove("startedAtNanos");
        metadata.put("caseCount", caseResults.size());
        writeArtifacts(objectMapper, runDirectory, metadata, caseResults);

        System.out.printf("F1.4E language runId=%s requested=%d providerCalls=%d output=%s%n",
                runId, CASES.size(), providerCalls.get(), runDirectory);
        if (providerCalls.get() != CASES.size()
                || caseResults.values().stream().anyMatch(result -> "TECHNICAL_ERROR".equals(
                        result.path("caseStatus").asText()))) {
            throw new AssertionError("Language evaluation did not complete successfully; see " + runDirectory);
        }
    }

    private static boolean isEnabled(String value) {
        return Boolean.parseBoolean(value);
    }

    private static ObjectNode initialMetadata(
            ObjectMapper objectMapper, String runId, String model, Duration timeout, Path runDirectory)
            throws Exception {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("runId", runId);
        metadata.put("startedAt", Instant.now().toString());
        metadata.put("startedAtNanos", System.nanoTime());
        metadata.put("provider", OpenAiGenerativeAiProvider.PROVIDER_ID);
        metadata.put("model", model);
        metadata.put("apiKeyPresent", true);
        metadata.put("promptId", PROMPT_ID);
        metadata.put("promptVersion", PROMPT_VERSION);
        metadata.put("schemaId", SCHEMA_ID);
        metadata.put("schemaVersion", SCHEMA_VERSION);
        metadata.put("promptSystemSha256", sha256Resource(
                "/prompts/intellectual-property-analysis/v0.3.1/system.md"));
        metadata.put("promptUserSha256", sha256Resource(
                "/prompts/intellectual-property-analysis/v0.3.1/user.md"));
        metadata.put("schemaSha256", sha256Resource("/ai-schemas/analysis-result/v3.0/schema.json"));
        metadata.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        metadata.put("timeout", timeout.toString());
        metadata.put("sdkRetries", 0);
        metadata.put("providerInternalRetryLoop", false);
        metadata.put("authorizedProviderCalls", CASES.size());
        metadata.put("providerGenerateCalls", 0);
        metadata.put("outputDirectory", runDirectory.toString());
        return metadata;
    }

    private static ObjectNode initialCaseResult(ObjectMapper objectMapper, EvaluationCase evaluationCase) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("caseId", evaluationCase.id());
        result.put("objective", evaluationCase.objective());
        result.put("caseStatus", "PENDING");
        result.put("jsonValid", false);
        result.put("schemaValid", false);
        result.put("domainValid", false);
        result.put("noScreeningInNarrative", false);
        return result;
    }

    private static void writeInput(ObjectMapper objectMapper, Path path, EvaluationCase evaluationCase)
            throws Exception {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("caseId", evaluationCase.id());
        input.put("objective", evaluationCase.objective());
        input.put("syntheticInput", evaluationCase.description());
        writeJsonNew(objectMapper, path, input);
    }

    private static void addNarrativeChecks(ObjectNode result, JsonNode response) {
        boolean found = containsScreeningInStringValues(response);
        result.put("noScreeningInNarrative", !found);
        result.put("screeningFoundInNarrative", found);
        result.put("summary", response.path("summary").asText(""));
        addNarrativeExcerpts(result, response);
    }

    private static boolean containsScreeningInStringValues(JsonNode node) {
        if (node.isTextual()) {
            return node.asText().toLowerCase(Locale.ROOT).contains("screening");
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsScreeningInStringValues(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addNarrativeExcerpts(ObjectNode result, JsonNode response) {
        ObjectNode excerpts = result.putObject("narrativeExcerpts");
        excerpts.put("summary", response.path("summary").asText(""));
        excerpts.put("article15Rationale", response.path("patentScreening").path("article15")
                .path("rationale").asText(""));
        excerpts.put("article20Rationale", response.path("patentScreening").path("article20")
                .path("rationale").asText(""));
    }

    private static void addLegalEvidence(ObjectNode result, JsonNode response) {
        JsonNode article15 = response.path("patentScreening").path("article15");
        JsonNode article20 = response.path("patentScreening").path("article20");
        result.put("article15Assessment", article15.path("assessment").asText(""));
        result.put("article15LiteralEReported", hasLiteral(article15.path("matches"), "e"));
        result.put("article15Rationale", article15.path("rationale").asText(""));
        result.put("article20Assessment", article20.path("assessment").asText(""));
        result.put("article20LiteralDReported", hasLiteral(article20.path("matches"), "d"));
        result.put("article20Rationale", article20.path("rationale").asText(""));
        result.put("inventionPatentOptionReported", hasProtectionOption(response, "INVENTION_PATENT"));
        result.put("copyrightOptionReported", hasProtectionOption(response, "COPYRIGHT"));
    }

    private static boolean hasLiteral(JsonNode matches, String literal) {
        if (!matches.isArray()) {
            return false;
        }
        for (JsonNode match : matches) {
            if (literal.equals(match.path("literal").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProtectionOption(JsonNode response, String type) {
        JsonNode options = response.path("protectionOptions");
        if (!options.isArray()) {
            return false;
        }
        for (JsonNode option : options) {
            if (type.equals(option.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode manualReviewChecklist(ObjectMapper objectMapper) {
        ObjectNode review = objectMapper.createObjectNode();
        review.put("status", "PENDING_MANUAL_REVIEW");
        review.put("summaryClarity", "PENDING");
        review.put("naturalSpanish", "PENDING");
        review.put("legalMeaningPreserved", "PENDING");
        review.put("unnecessaryTechnicalTerms", "PENDING");
        review.put("repeatedExplanation", "PENDING");
        review.put("inventedTechnicalEffectsOrObjects", "PENDING");
        review.put("definitiveLegalConclusion", "PENDING");
        review.put("classificationChanges", "PENDING");
        return review;
    }

    private static void addTokenUsage(ObjectNode result, GenerativeAiResponse response) {
        if (response.tokenUsage().inputTokens() != null) {
            result.put("inputTokens", response.tokenUsage().inputTokens());
        }
        if (response.tokenUsage().outputTokens() != null) {
            result.put("outputTokens", response.tokenUsage().outputTokens());
        }
        if (response.tokenUsage().totalTokens() != null) {
            result.put("totalTokens", response.tokenUsage().totalTokens());
        }
    }

    private static void writeArtifacts(
            ObjectMapper objectMapper,
            Path runDirectory,
            ObjectNode metadata,
            Map<String, ObjectNode> caseResults) throws Exception {
        ArrayNode cases = objectMapper.createArrayNode();
        caseResults.values().forEach(result -> cases.add(result.deepCopy()));
        ObjectNode results = objectMapper.createObjectNode();
        results.set("metadata", metadata.deepCopy());
        results.set("cases", cases);
        writeJson(objectMapper, runDirectory.resolve("metadata.json"), metadata);
        writeJson(objectMapper, runDirectory.resolve("results.json"), results);
    }

    private static void writeJsonNew(ObjectMapper objectMapper, Path path, JsonNode json) throws Exception {
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
                + System.lineSeparator();
        Files.writeString(path, formatted, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    private static void writeJson(ObjectMapper objectMapper, Path path, JsonNode json) throws Exception {
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
                + System.lineSeparator();
        Files.writeString(path, formatted, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String sha256Resource(String resourcePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = OpenAiF14ELanguageLiveEvalIT.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Required live evaluation resource is missing");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static Duration timeout(String configured) {
        return configured == null || configured.isBlank()
                ? Duration.ofSeconds(60)
                : DurationStyle.detectAndParse(configured);
    }

    private record EvaluationCase(String id, String objective, String description) {
    }
}
