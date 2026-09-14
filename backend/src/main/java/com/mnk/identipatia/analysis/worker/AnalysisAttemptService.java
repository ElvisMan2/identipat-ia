package com.mnk.identipatia.analysis.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.RenderedPrompt;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import com.mnk.identipatia.ai.model.TokenUsage;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.model.AiInvocation;
import com.mnk.identipatia.analysis.model.AiInvocationStatus;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisFailureCode;
import com.mnk.identipatia.analysis.model.AnalysisInput;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AiInvocationRepository;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisAttemptService {
    static final PromptReference PROMPT = new PromptReference("intellectual-property-analysis", "0.1");
    static final String SCHEMA_ID = "analysis-result";
    static final String SCHEMA_VERSION = "1.0";
    private static final String WORKER_LEASE_LOST = "WORKER_LEASE_LOST";
    private static final String WORKER_LEASE_LOST_MESSAGE = "The previous worker lease expired before completion";
    private static final String MAX_ATTEMPTS_MESSAGE = "The analysis could not be completed within the configured attempt limit";

    private final AnalysisRepository analysisRepository;
    private final AnalysisInputRepository inputRepository;
    private final AiInvocationRepository invocationRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final PromptRenderer promptRenderer;
    private final OutputSchemaRegistry schemaRegistry;
    private final AnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnalysisAttemptService(
            AnalysisRepository analysisRepository,
            AnalysisInputRepository inputRepository,
            AiInvocationRepository invocationRepository,
            StoredAnalysisResultRepository resultRepository,
            PromptRenderer promptRenderer,
            OutputSchemaRegistry schemaRegistry,
            AnalysisProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.inputRepository = inputRepository;
        this.invocationRepository = invocationRepository;
        this.resultRepository = resultRepository;
        this.promptRenderer = promptRenderer;
        this.schemaRegistry = schemaRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Optional<AttemptContext> prepare(UUID analysisId, String workerId, GenerativeAiProvider provider) {
        Instant now = clock.instant();
        Analysis analysis = requireActiveLease(analysisId, workerId, now);
        invocationRepository.findFirstByAnalysisIdAndStatusOrderByAttemptNumberDesc(
                        analysisId, AiInvocationStatus.STARTED)
                .ifPresent(stale -> abandon(stale, now));

        long previousAttempts = invocationRepository.countByAnalysisId(analysisId);
        if (previousAttempts >= properties.maxAttempts()) {
            failAnalysis(analysis, AnalysisFailureCode.WORKER_PROCESSING_ERROR, MAX_ATTEMPTS_MESSAGE, now);
            clearLease(analysis);
            analysis.setUpdatedAt(now);
            return Optional.empty();
        }

        AnalysisInput input = inputRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("Analysis input is missing"));
        RenderedPrompt rendered = promptRenderer.render(PROMPT,
                Map.of("USER_DESCRIPTION", input.getProcessedText()));
        StructuredOutputDefinition output = schemaRegistry.load(SCHEMA_ID, SCHEMA_VERSION);
        GenerationOptions options = new GenerationOptions(properties.aiMaxOutputTokens(), null);

        AiInvocation invocation = new AiInvocation();
        invocation.setInvocationId(UUID.randomUUID());
        invocation.setAnalysisId(analysisId);
        invocation.setAttemptNumber(Math.toIntExact(previousAttempts + 1));
        invocation.setStatus(AiInvocationStatus.STARTED);
        invocation.setProvider(provider.providerId());
        invocation.setPromptId(rendered.reference().promptId());
        invocation.setPromptVersion(rendered.reference().version());
        invocation.setTemplateHash(rendered.templateHash());
        invocation.setRenderedHash(rendered.renderedHash());
        invocation.setRenderedPromptSnapshot(rendered.renderedSnapshot());
        invocation.setOutputSchemaId(output.schemaId());
        invocation.setOutputSchemaVersion(output.schemaVersion());
        invocation.setRequestParameters(requestParameters(options));
        invocation.setCreatedAt(now);
        invocationRepository.saveAndFlush(invocation);

        return Optional.of(new AttemptContext(invocation.getInvocationId(), invocation.getAttemptNumber(),
                new GenerativeAiRequest(rendered, output, options)));
    }

    @Transactional
    public void succeed(UUID analysisId, UUID invocationId, String workerId,
            GenerativeAiResponse response, AnalysisResult result) {
        Instant now = clock.instant();
        Analysis analysis = requireActiveLease(analysisId, workerId, now);
        AiInvocation invocation = requireStartedInvocation(invocationId, analysisId);

        invocation.setStatus(AiInvocationStatus.SUCCEEDED);
        invocation.setProvider(response.provider().provider());
        invocation.setModel(response.provider().model());
        invocation.setProviderRequestId(response.provider().providerRequestId());
        invocation.setRawProviderResponse(response.rawProviderResponse());
        invocation.setStructuredProviderResponse(response.structuredOutput());
        TokenUsage usage = response.tokenUsage();
        invocation.setInputTokens(usage.inputTokens());
        invocation.setOutputTokens(usage.outputTokens());
        invocation.setTotalTokens(usage.totalTokens());
        invocation.setTokenUsage(tokenUsage(usage));
        invocation.setLatencyMs(response.latencyMs());
        invocation.setFinishReason(response.finishReason());
        invocation.setRetryable(false);
        invocation.setCompletedAt(now);

        StoredAnalysisResult stored = new StoredAnalysisResult();
        stored.setAnalysisId(analysisId);
        stored.setSchemaVersion(result.schemaVersion());
        stored.setResultJson(objectMapper.valueToTree(result));
        stored.setCreatedAt(now);
        resultRepository.save(stored);

        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(now);
        analysis.setFailedAt(null);
        analysis.setFailureCode(null);
        analysis.setFailureMessage(null);
        analysis.setNextAttemptAt(null);
        clearLease(analysis);
        analysis.setUpdatedAt(now);
    }

    @Transactional
    public void fail(UUID analysisId, UUID invocationId, int attemptNumber, String workerId,
            AnalysisFailureCode code, AiInvocationStatus invocationStatus,
            boolean retryable, String sanitizedMessage) {
        Instant now = clock.instant();
        Analysis analysis = requireActiveLease(analysisId, workerId, now);
        AiInvocation invocation = requireStartedInvocation(invocationId, analysisId);
        invocation.setStatus(invocationStatus);
        invocation.setErrorCode(code.name());
        invocation.setErrorMessage(sanitizedMessage);
        invocation.setRetryable(retryable);
        invocation.setCompletedAt(now);

        if (retryable && attemptNumber < properties.maxAttempts()) {
            analysis.setStatus(AnalysisStatus.ANALYZING);
            analysis.setNextAttemptAt(now.plus(properties.retryDelay()));
            analysis.setFailureCode(null);
            analysis.setFailureMessage(null);
        } else {
            failAnalysis(analysis, code, sanitizedMessage, now);
        }
        clearLease(analysis);
        analysis.setUpdatedAt(now);
    }

    @Transactional
    public void failClaimedWithoutInvocation(UUID analysisId, String workerId) {
        Instant now = clock.instant();
        Analysis analysis = requireActiveLease(analysisId, workerId, now);
        failAnalysis(analysis, AnalysisFailureCode.WORKER_PROCESSING_ERROR,
                "The analysis worker could not prepare the request", now);
        clearLease(analysis);
        analysis.setUpdatedAt(now);
    }

    private Analysis requireActiveLease(UUID analysisId, String workerId, Instant now) {
        Analysis analysis = analysisRepository.findByAnalysisIdAndLeaseOwnerAndStatus(
                        analysisId, workerId, AnalysisStatus.ANALYZING)
                .orElseThrow(AnalysisLeaseLostException::new);
        if (analysis.getLeaseUntil() == null || !now.isBefore(analysis.getLeaseUntil())) {
            throw new AnalysisLeaseLostException();
        }
        return analysis;
    }

    private AiInvocation requireStartedInvocation(UUID invocationId, UUID analysisId) {
        AiInvocation invocation = invocationRepository.findById(invocationId)
                .orElseThrow(() -> new IllegalStateException("AI invocation is missing"));
        if (!analysisId.equals(invocation.getAnalysisId()) || invocation.getStatus() != AiInvocationStatus.STARTED) {
            throw new IllegalStateException("AI invocation is not active");
        }
        return invocation;
    }

    private void abandon(AiInvocation invocation, Instant now) {
        invocation.setStatus(AiInvocationStatus.ABANDONED);
        invocation.setCompletedAt(now);
        invocation.setErrorCode(WORKER_LEASE_LOST);
        invocation.setErrorMessage(WORKER_LEASE_LOST_MESSAGE);
        invocation.setRetryable(true);
    }

    private void failAnalysis(Analysis analysis, AnalysisFailureCode code, String message, Instant now) {
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setFailedAt(now);
        analysis.setFailureCode(code.name());
        analysis.setFailureMessage(message);
        analysis.setNextAttemptAt(null);
    }

    private static void clearLease(Analysis analysis) {
        analysis.setLeaseOwner(null);
        analysis.setLeaseUntil(null);
    }

    private ObjectNode requestParameters(GenerationOptions options) {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("maxOutputTokens", options.maxOutputTokens());
        parameters.putNull("temperature");
        return parameters;
    }

    private JsonNode tokenUsage(TokenUsage usage) {
        ObjectNode node = objectMapper.createObjectNode();
        putNullable(node, "inputTokens", usage.inputTokens());
        putNullable(node, "outputTokens", usage.outputTokens());
        putNullable(node, "totalTokens", usage.totalTokens());
        node.set("providerDetails", usage.providerDetails());
        return node;
    }

    private static void putNullable(ObjectNode node, String name, Long value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }

    public record AttemptContext(UUID invocationId, int attemptNumber, GenerativeAiRequest request) {
    }
}
