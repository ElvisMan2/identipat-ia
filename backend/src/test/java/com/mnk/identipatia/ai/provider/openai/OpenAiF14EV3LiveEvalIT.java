package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in live evaluation. The corresponding expectation engine is tested offline. */
class OpenAiF14EV3LiveEvalIT {
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 6000;
    private static final String PROMPT_ID = "intellectual-property-analysis";
    private static final String PROMPT_VERSION = "0.3";
    private static final String SCHEMA_ID = "analysis-result";
    private static final String SCHEMA_VERSION = "3.0";
    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    @Test
    void evaluatesSelectedF14EV3CasesAgainstOpenAi() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("RUN_F14E_V3_LIVE_EVAL")),
                "Set RUN_F14E_V3_LIVE_EVAL=true to enable the manual evaluation");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank(),
                "OPENAI_API_KEY and OPENAI_MODEL are required");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String suite = System.getenv().getOrDefault("F14E_V3_LIVE_EVAL_SUITE", "golden");
        JsonNode fixture = loadCases(objectMapper, suite);
        JsonNode conditionalRules = loadConditionalRules(objectMapper);
        List<JsonNode> selectedCases = selectedCases(fixture);
        Set<String> selectedIds = selectedCases.stream().map(testCase -> testCase.path("id").asText())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int maxOutputTokens = positiveInteger(
                System.getenv("F14E_V3_LIVE_EVAL_MAX_OUTPUT_TOKENS"), DEFAULT_MAX_OUTPUT_TOKENS);
        Duration timeout = timeout(System.getenv("OPENAI_TIMEOUT"));
        String systemPromptHash = sha256Resource("/prompts/intellectual-property-analysis/v0.3/system.md");
        String userPromptHash = sha256Resource("/prompts/intellectual-property-analysis/v0.3/user.md");
        String schemaHash = sha256Resource("/ai-schemas/analysis-result/v3.0/schema.json");

        String runId = RUN_STAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path runDirectory = Path.of(System.getProperty("basedir", "."), "target", "f1.4e-v3-live-eval", runId);
        Files.createDirectories(runDirectory.getParent());
        Files.createDirectory(runDirectory);

        LinkedHashMap<String, ObjectNode> caseRecords = new LinkedHashMap<>();
        Set<String> preparationFailures = new LinkedHashSet<>();
        for (JsonNode testCase : selectedCases) {
            String caseId = testCase.path("id").asText();
            ObjectNode record = initialCaseRecord(objectMapper, testCase);
            caseRecords.put(caseId, record);
            try {
                writeCaseEvidence(objectMapper, runDirectory, testCase, record);
            } catch (Exception persistenceException) {
                preparationFailures.add(caseId);
                record.put("caseStatus", "TECHNICAL_ERROR");
                record.put("evidencePersistenceErrorType", persistenceException.getClass().getName());
                addTechnicalFailure(record, persistenceException.getClass().getName());
            }
        }

        ObjectNode runMetadata = initialRunMetadata(objectMapper, runId, model, selectedIds,
                maxOutputTokens, timeout, runDirectory, systemPromptHash, userPromptHash, schemaHash);
        String fixtureResource = switch (suite) {
            case "contrast" -> "/f1.4e/contrast-cases-v1.json";
            case "contrast-v2" -> "/f1.4e/contrast-cases-v2.json";
            case "pairs" -> "/f1.4e/contrast-minimal-pairs-v1.json";
            default -> "/f1.4e/legal-evaluation-cases-v3.json";
        };
        runMetadata.put("suite", suite);
        runMetadata.put("fixtureResource", fixtureResource);
        runMetadata.put("fixtureSha256", sha256Resource(fixtureResource));
        runMetadata.put("conditionalApplicabilityRulesVersion", conditionalRules.path("version").asText());
        runMetadata.put("conditionalApplicabilityRulesSha256",
                sha256Resource("/f1.4e/conditional-applicability-v1.json"));
        writeRunArtifacts(objectMapper, runDirectory, runMetadata, caseRecords);

        PromptRenderer renderer = new PromptRenderer(new PromptRegistry());
        OutputSchemaRegistry schemas = new OutputSchemaRegistry(objectMapper);
        AnalysisResultReader reader = new AnalysisResultReader(objectMapper);
        F14EV3ExpectationEvaluator expectationEvaluator = new F14EV3ExpectationEvaluator();
        int providerAttempts = 0;
        long runStartedNanos = System.nanoTime();
        OpenAIClient client = null;
        String setupError = null;
        try {
            client = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(timeout)
                    .maxRetries(0)
                    .build();
        } catch (Exception | AssertionError exception) {
            setupError = exception.getClass().getName();
            for (ObjectNode record : caseRecords.values()) {
                record.put("errorType", setupError);
                record.put("caseStatus", "TECHNICAL_ERROR");
                addTechnicalFailure(record, setupError);
            }
        }

        if (client != null) {
            try {
                for (JsonNode testCase : selectedCases) {
                    String id = testCase.path("id").asText();
                    ObjectNode record = caseRecords.get(id);
                    if (preparationFailures.contains(id)) {
                        continue;
                    }
                    long caseStartedNanos = System.nanoTime();
                    providerAttempts++;
                    Path responseFile = runDirectory.resolve(id + ".json");
                    AtomicReference<String> captureFailure = new AtomicReference<>();
                    StructuredOutputValidator capturingValidator = new StructuredOutputValidator() {
                        @Override
                        public void validate(
                                StructuredOutputDefinition definition,
                                JsonNode output,
                                String providerId) {
                            record.put("executionStatus", "EXECUTED");
                            try {
                                writeStructuredOutput(objectMapper, responseFile, output);
                                record.put("structuredOutputCapturedBeforeSchemaValidation", true);
                                record.put("structuredOutputFile", responseFile.getFileName().toString());
                            } catch (Exception persistenceException) {
                                captureFailure.set(persistenceException.getClass().getName());
                                record.put("structuredOutputCapturedBeforeSchemaValidation", false);
                            }
                            try {
                                super.validate(definition, output, providerId);
                                record.put("schemaValid", true);
                            } catch (RuntimeException validationException) {
                                record.put("schemaValid", false);
                                throw validationException;
                            }
                        }
                    };
                    OpenAiGenerativeAiProvider provider = new OpenAiGenerativeAiProvider(
                            client, model, objectMapper, capturingValidator);
                    try {
                        GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                                renderer.render(new PromptReference(PROMPT_ID, PROMPT_VERSION),
                                        Map.of("USER_DESCRIPTION", testCase.path("input").asText())),
                                schemas.load(SCHEMA_ID, SCHEMA_VERSION),
                                new GenerationOptions(maxOutputTokens, null)));
                        if (captureFailure.get() != null) {
                            throw new IllegalStateException("Structured output capture failed: " + captureFailure.get());
                        }

                        // The capturing validator persists JSON before schema validation; from here,
                        // Java-domain and fixture checks can fail without losing the inference.
                        JsonNode structuredOutput = response.structuredOutput();
                        record.put("executionStatus", "EXECUTED");
                        record.put("schemaValid", true);
                        record.put("provider", response.provider().provider());
                        record.put("model", response.provider().model());
                        record.put("durationMs", response.latencyMs());
                        addTokenMetrics(record, response);
                        if (response.finishReason() == null) {
                            record.putNull("finishReason");
                        } else {
                            record.put("finishReason", response.finishReason());
                        }
                        record.put("structuredOutputFile", responseFile.getFileName().toString());
                        record.set("conditionalReview", conditionalReviews(objectMapper, testCase,
                                responseFile.getFileName().toString(), conditionalRules));

                        try {
                            AnalysisResultV3 result = reader.readCurrent(structuredOutput);
                            record.put("domainValid", true);
                            record.put("domainSchemaVersion", result.schemaVersion());
                            if (!AnalysisResultV3.SCHEMA_VERSION.equals(result.schemaVersion())) {
                                record.put("domainValid", false);
                                addTechnicalFailure(record, "Unexpected AnalysisResultV3 schemaVersion");
                            }
                        } catch (Exception | AssertionError exception) {
                            record.put("domainValid", false);
                            addTechnicalFailure(record, exception.getClass().getName());
                        }

                        if (record.path("domainValid").asBoolean(false)) {
                            F14EV3ExpectationEvaluator.Evaluation evaluation =
                                    expectationEvaluator.evaluate(testCase, structuredOutput);
                            ArrayNode checks = objectMapper.createArrayNode();
                            evaluation.checks().forEach(check -> checks.add(checkJson(objectMapper, check)));
                            record.set("expectationChecks", checks);
                            record.put("deterministicPassed", evaluation.passed());
                            record.put("deterministicFailed", evaluation.failed());
                            record.put("needsExpertReview", evaluation.needsExpertReview());
                            record.put("unsupportedOperators", evaluation.unsupportedOperators());
                            record.put("mustHavePass", groupPasses(evaluation, "mustHave"));
                            record.put("mustNotHavePass", groupPasses(evaluation, "mustNotHave"));
                            if (evaluation.failed() > 0 || evaluation.unsupportedOperators() > 0) {
                                record.put("caseStatus", "FAILED");
                            } else if (!evaluation.approvedWithoutExpertReview()
                                    || hasConditionalReview(record.path("conditionalReview"))) {
                                record.put("caseStatus", "NEEDS_EXPERT_REVIEW");
                            } else {
                                record.put("caseStatus", "APPROVED");
                            }
                        } else {
                            record.put("caseStatus", "TECHNICAL_ERROR");
                        }
                    } catch (Exception | AssertionError exception) {
                        if (exception instanceof GenerativeAiException aiException
                                && "INVALID_RESPONSE".equals(aiException.type().name())) {
                            record.put("schemaValid", false);
                        }
                        if (!"EXECUTED".equals(record.path("executionStatus").asText())) {
                            record.put("executionStatus", "NOT_EXECUTED");
                        }
                        record.put("caseStatus", "TECHNICAL_ERROR");
                        addTechnicalFailure(record, exception.getClass().getName());
                    } finally {
                        record.put("attempted", true);
                        record.put("elapsedMs", Duration.ofNanos(System.nanoTime() - caseStartedNanos).toMillis());
                        try {
                            writeCaseEvidence(objectMapper, runDirectory, testCase, record);
                        } catch (Exception persistenceException) {
                            record.put("evidencePersistenceErrorType", persistenceException.getClass().getName());
                            record.put("caseStatus", "TECHNICAL_ERROR");
                            addTechnicalFailure(record, persistenceException.getClass().getName());
                        }
                        try {
                            writeRunArtifacts(objectMapper, runDirectory, runMetadata, caseRecords);
                        } catch (Exception persistenceException) {
                            record.put("runArtifactPersistenceErrorType", persistenceException.getClass().getName());
                            record.put("caseStatus", "TECHNICAL_ERROR");
                            addTechnicalFailure(record, persistenceException.getClass().getName());
                        }
                    }
                }
            } finally {
                client.close();
            }
        }

        long runDurationMs = Duration.ofNanos(System.nanoTime() - runStartedNanos).toMillis();
        ObjectNode counts = countResults(caseRecords);
        runMetadata.put("providerAttempts", providerAttempts);
        runMetadata.put("durationMs", runDurationMs);
        runMetadata.set("counts", counts);
        runMetadata.put("setupErrorType", setupError == null ? "" : setupError);
        writeRunArtifacts(objectMapper, runDirectory, runMetadata, caseRecords);

        long failed = counts.path("failed").asLong();
        long notExecuted = counts.path("notExecuted").asLong();
        long unsupported = counts.path("unsupportedOperators").asLong();
        System.out.printf("F1.4E V3 runId=%s requested=%d executed=%d approved=%d failed=%d notExecuted=%d "
                        + "needsExpertReview=%d unsupportedOperators=%d output=%s%n",
                runId, counts.path("requested").asLong(), counts.path("executed").asLong(),
                counts.path("approved").asLong(), failed, notExecuted,
                counts.path("needsExpertReview").asLong(), unsupported, runDirectory.toAbsolutePath());
        if (failed > 0 || notExecuted > 0 || unsupported > 0
                || counts.path("requested").asLong() != selectedIds.size()) {
            fail("F1.4E V3 live evaluation incomplete or failed. runId=" + runId
                    + ", requested=" + counts.path("requested").asLong()
                    + ", executed=" + counts.path("executed").asLong()
                    + ", approved=" + counts.path("approved").asLong()
                    + ", failed=" + failed
                    + ", notExecuted=" + notExecuted
                    + ", needsExpertReview=" + counts.path("needsExpertReview").asLong()
                    + ", unsupportedOperators=" + unsupported
                    + ". See " + runDirectory.toAbsolutePath());
        }
    }

    static JsonNode loadCases(ObjectMapper objectMapper, String suite) throws Exception {
        String resource = switch (suite) {
            case "golden" -> "/f1.4e/legal-evaluation-cases-v3.json";
            case "contrast" -> "/f1.4e/contrast-cases-v1.json";
            case "contrast-v2" -> "/f1.4e/contrast-cases-v2.json";
            case "pairs" -> "/f1.4e/contrast-minimal-pairs-v1.json";
            default -> throw new IllegalArgumentException("Unknown F1.4E V3 suite: " + suite);
        };
        try (InputStream stream = OpenAiF14EV3LiveEvalIT.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("F1.4E V3 evaluation fixture is missing: " + resource);
            }
            JsonNode root = objectMapper.readTree(stream);
            if (suite.equals("pairs")) {
                ArrayNode flattened = objectMapper.createArrayNode();
                for (JsonNode pair : root.path("pairs")) {
                    for (JsonNode testCase : pair.path("cases")) {
                        ObjectNode withPairId = testCase.deepCopy();
                        withPairId.put("pairId", pair.path("pairId").asText());
                        flattened.add(withPairId);
                    }
                }
                return flattened;
            }
            return root.path("cases");
        }
    }

    private static List<JsonNode> selectedCases(JsonNode cases) {
        String configured = System.getenv("F14E_V3_LIVE_EVAL_CASES");
        return selectCases(cases, configured);
    }

    static List<JsonNode> selectCases(JsonNode cases, String configured) {
        Set<String> selected = configured == null || configured.isBlank()
                ? Set.of("V3-08")
                : Arrays.stream(configured.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("F14E_V3_LIVE_EVAL_CASES must select at least one case");
        }
        Set<String> available = new LinkedHashSet<>();
        cases.forEach(testCase -> available.add(testCase.path("id").asText()));
        Set<String> unknown = new LinkedHashSet<>(selected);
        unknown.removeAll(available);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown F1.4E V3 case ids: " + unknown);
        }
        return cases.findParents("id").stream()
                .filter(testCase -> selected.contains(testCase.path("id").asText()))
                .toList();
    }

    private static int positiveInteger(String configured, int defaultValue) {
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(configured.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException("Live evaluation token limit must be positive");
        }
        return parsed;
    }

    private static Duration timeout(String configured) {
        return configured == null || configured.isBlank()
                ? Duration.ofSeconds(60)
                : DurationStyle.detectAndParse(configured);
    }

    private static ObjectNode initialCaseRecord(ObjectMapper objectMapper, JsonNode testCase) {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("caseId", testCase.path("id").asText());
        record.put("title", testCase.path("title").asText());
        record.put("executionStatus", "NOT_EXECUTED");
        record.put("caseStatus", "NOT_EXECUTED");
        record.putNull("schemaValid");
        record.putNull("domainValid");
        record.putNull("mustHavePass");
        record.putNull("mustNotHavePass");
        record.put("attempted", false);
        record.put("promptVersion", PROMPT_VERSION);
        record.put("schemaVersion", SCHEMA_VERSION);
        return record;
    }

    private static ObjectNode initialRunMetadata(
            ObjectMapper objectMapper,
            String runId,
            String model,
            Set<String> selectedIds,
            int maxOutputTokens,
            Duration timeout,
            Path runDirectory,
            String systemPromptHash,
            String userPromptHash,
            String schemaHash) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("runId", runId);
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("provider", OpenAiGenerativeAiProvider.PROVIDER_ID);
        metadata.put("requestedModel", model);
        metadata.put("promptId", PROMPT_ID);
        metadata.put("promptVersion", PROMPT_VERSION);
        metadata.put("systemPromptSha256", systemPromptHash);
        metadata.put("userPromptSha256", userPromptHash);
        metadata.put("schemaId", SCHEMA_ID);
        metadata.put("schemaVersion", SCHEMA_VERSION);
        metadata.put("schemaSha256", schemaHash);
        metadata.put("schemaValidationSource", "OpenAiGenerativeAiProvider + StructuredOutputValidator");
        metadata.put("domainValidationSource", "AnalysisResultReader.readCurrent + AnalysisResultV3 invariants");
        metadata.put("activationVariable", "RUN_F14E_V3_LIVE_EVAL");
        metadata.put("selectorVariable", "F14E_V3_LIVE_EVAL_CASES");
        metadata.put("tokenLimitVariable", "F14E_V3_LIVE_EVAL_MAX_OUTPUT_TOKENS");
        metadata.put("maxOutputTokens", maxOutputTokens);
        metadata.put("timeout", timeout.toString());
        metadata.put("maxRetries", 0);
        metadata.putNull("temperature");
        metadata.put("outputDirectory", runDirectory.toAbsolutePath().toString());
        ArrayNode requested = objectMapper.createArrayNode();
        selectedIds.forEach(requested::add);
        metadata.set("requestedIds", requested);
        metadata.put("rawProviderResponsePersisted", false);
        metadata.put("structuredOutputPersistedBeforeAssertions", true);
        return metadata;
    }

    private static void addTokenMetrics(ObjectNode record, GenerativeAiResponse response) {
        if (response.tokenUsage() == null) {
            record.putNull("inputTokens");
            record.putNull("outputTokens");
            return;
        }
        if (response.tokenUsage().inputTokens() == null) {
            record.putNull("inputTokens");
        } else {
            record.put("inputTokens", response.tokenUsage().inputTokens());
        }
        if (response.tokenUsage().outputTokens() == null) {
            record.putNull("outputTokens");
        } else {
            record.put("outputTokens", response.tokenUsage().outputTokens());
        }
    }

    private static ObjectNode checkJson(ObjectMapper mapper, F14EV3ExpectationEvaluator.CheckResult check) {
        ObjectNode json = mapper.createObjectNode();
        json.put("group", check.group());
        json.put("index", check.index());
        json.put("path", check.path());
        json.put("operator", check.operator());
        if (check.expected() == null) {
            json.putNull("expected");
        } else {
            json.set("expected", check.expected().deepCopy());
        }
        json.put("status", check.status().name());
        json.put("actual", check.actual());
        json.put("evidence", check.evidence());
        json.put("rationale", check.rationale());
        return json;
    }

    private static ArrayNode conditionalReviews(ObjectMapper mapper, JsonNode testCase, String outputFile) {
        return conditionalReviews(mapper, testCase, outputFile, null);
    }

    private static ArrayNode conditionalReviews(
            ObjectMapper mapper, JsonNode testCase, String outputFile, JsonNode applicabilityRules) {
        ArrayNode reviews = mapper.createArrayNode();
        List<F14EV3ExpectationEvaluator.CheckResult> evaluated =
                new F14EV3ExpectationEvaluator().evaluateConditionalSignals(testCase, applicabilityRules);
        for (F14EV3ExpectationEvaluator.CheckResult check : evaluated) {
            JsonNode signal = testCase.path("conditionalSignals").get(check.index());
            ObjectNode review = mapper.createObjectNode();
            review.put("status", check.status().name());
            review.put("condition", signal.path("condition").asText());
            review.put("assertion", signal.path("assertion").asText());
            review.put("evidenceFile", outputFile);
            review.put("evidence", check.evidence());
            review.put("rationale", check.rationale());
            reviews.add(review);
        }
        return reviews;
    }

    private static JsonNode loadConditionalRules(ObjectMapper mapper) throws Exception {
        try (InputStream stream = OpenAiF14EV3LiveEvalIT.class.getResourceAsStream(
                "/f1.4e/conditional-applicability-v1.json")) {
            if (stream == null) throw new IllegalStateException("Conditional applicability rules are missing");
            return mapper.readTree(stream);
        }
    }

    private static boolean hasConditionalReview(JsonNode reviews) {
        for (JsonNode review : reviews) {
            if ("NEEDS_EXPERT_REVIEW".equals(review.path("status").asText())) return true;
        }
        return false;
    }

    private static Boolean groupPasses(F14EV3ExpectationEvaluator.Evaluation evaluation, String group) {
        List<F14EV3ExpectationEvaluator.CheckResult> grouped = evaluation.checks().stream()
                .filter(check -> check.group().equals(group)).toList();
        if (grouped.isEmpty()) {
            return null;
        }
        if (grouped.stream().anyMatch(check -> check.status() == F14EV3ExpectationEvaluator.Status.FAIL
                || check.status() == F14EV3ExpectationEvaluator.Status.UNSUPPORTED_OPERATOR)) {
            return false;
        }
        if (grouped.stream().anyMatch(check -> check.status() == F14EV3ExpectationEvaluator.Status.NEEDS_EXPERT_REVIEW)) {
            return null;
        }
        return true;
    }

    private static ObjectNode countResults(Map<String, ObjectNode> caseRecords) {
        ObjectMapper mapper = new ObjectMapper();
        long executed = caseRecords.values().stream()
                .filter(record -> "EXECUTED".equals(record.path("executionStatus").asText())).count();
        long approved = caseRecords.values().stream()
                .filter(record -> "APPROVED".equals(record.path("caseStatus").asText())).count();
        long failed = caseRecords.values().stream()
                .filter(record -> Set.of("FAILED", "TECHNICAL_ERROR").contains(record.path("caseStatus").asText())).count();
        long notExecuted = caseRecords.values().stream()
                .filter(record -> "NOT_EXECUTED".equals(record.path("executionStatus").asText())).count();
        long needsExpertReview = caseRecords.values().stream()
                .filter(record -> "NEEDS_EXPERT_REVIEW".equals(record.path("caseStatus").asText())).count();
        long unsupported = caseRecords.values().stream().mapToLong(record -> record.path("unsupportedOperators").asLong()).sum();
        ObjectNode counts = mapper.createObjectNode();
        counts.put("requested", caseRecords.size());
        counts.put("executed", executed);
        counts.put("approved", approved);
        counts.put("failed", failed);
        counts.put("notExecuted", notExecuted);
        counts.put("needsExpertReview", needsExpertReview);
        counts.put("unsupportedOperators", unsupported);
        return counts;
    }

    private static void addTechnicalFailure(ObjectNode record, String errorType) {
        ArrayNode failures = (ArrayNode) record.withArray("technicalErrors");
        failures.add(errorType);
    }

    private static void writeCaseEvidence(
            ObjectMapper objectMapper,
            Path runDirectory,
            JsonNode testCase,
            ObjectNode record) throws Exception {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("caseId", testCase.path("id").asText());
        evidence.put("title", testCase.path("title").asText());
        if (testCase.has("pairId")) evidence.put("pairId", testCase.path("pairId").asText());
        evidence.put("syntheticInput", testCase.path("input").asText());
        if (testCase.has("expectedBehavior")) evidence.put("expectedBehavior", testCase.path("expectedBehavior").asText());
        if (testCase.has("justification")) evidence.put("justification", testCase.path("justification").asText());
        if (testCase.has("deltaFromPairBaseline")) {
            evidence.put("deltaFromPairBaseline", testCase.path("deltaFromPairBaseline").asText());
        }
        evidence.set("mustHave", testCase.path("positiveSignals").deepCopy());
        evidence.set("mustNotHave", testCase.path("negativeSignals").deepCopy());
        evidence.set("conditionalExpectations", testCase.path("conditionalSignals").deepCopy());
        evidence.set("evaluation", record.deepCopy());
        writeJson(objectMapper, runDirectory.resolve(testCase.path("id").asText() + "-evidence.json"), evidence);
    }

    private static void writeStructuredOutput(ObjectMapper objectMapper, Path path, JsonNode output) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + System.lineSeparator();
        Files.writeString(path, json, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    private static void writeRunArtifacts(
            ObjectMapper objectMapper,
            Path runDirectory,
            ObjectNode metadata,
            Map<String, ObjectNode> caseRecords) throws Exception {
        ArrayNode records = objectMapper.createArrayNode();
        caseRecords.values().forEach(record -> records.add(record.deepCopy()));
        ObjectNode results = objectMapper.createObjectNode();
        results.set("metadata", metadata.deepCopy());
        results.set("cases", records);
        writeJson(objectMapper, runDirectory.resolve("results.json"), results);
        writeJson(objectMapper, runDirectory.resolve("execution-metadata.json"), metadata);
    }

    private static void writeJson(ObjectMapper objectMapper, Path path, JsonNode json) throws Exception {
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json) + System.lineSeparator();
        Files.writeString(path, formatted, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String sha256Resource(String resourcePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = OpenAiF14EV3LiveEvalIT.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Required evaluation resource is missing: " + resourcePath);
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
    }
}
