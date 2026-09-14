package com.mnk.identipatia.analysis.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.model.AiInvocationStatus;
import com.mnk.identipatia.analysis.model.AnalysisFailureCode;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Component
public class AnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);
    private final AnalysisProperties properties;
    private final AnalysisClaimService claimService;
    private final AnalysisAttemptService attemptService;
    private final ObjectProvider<GenerativeAiProvider> providerSource;
    private final StructuredOutputValidator validator;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;
    private final String workerId;

    public AnalysisWorker(
            AnalysisProperties properties,
            AnalysisClaimService claimService,
            AnalysisAttemptService attemptService,
            ObjectProvider<GenerativeAiProvider> providerSource,
            StructuredOutputValidator validator,
            ObjectMapper objectMapper,
            @Qualifier("analysisTaskExecutor") ThreadPoolTaskExecutor executor,
            @Qualifier("analysisWorkerId") String workerId) {
        this.properties = properties;
        this.claimService = claimService;
        this.attemptService = attemptService;
        this.providerSource = providerSource;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.workerId = workerId;
    }

    @Scheduled(fixedDelayString = "#{@analysisPollIntervalMillis}")
    public void scheduledPoll() {
        if (properties.workerEnabled()) {
            pollOnce();
        }
    }

    public int pollOnce() {
        Optional<GenerativeAiProvider> provider = providerSource.orderedStream().findFirst();
        if (provider.isEmpty()) {
            return 0;
        }

        int capacity = Math.max(0, properties.workerThreads() - executor.getActiveCount())
                + executor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int submitted = 0;
        for (int index = 0; index < capacity; index++) {
            Optional<UUID> claimed = claimService.claimNext(workerId);
            if (claimed.isEmpty()) {
                break;
            }
            UUID analysisId = claimed.orElseThrow();
            try {
                executor.execute(() -> process(analysisId, provider.orElseThrow()));
                submitted++;
            } catch (RejectedExecutionException exception) {
                claimService.release(analysisId, workerId);
                break;
            }
        }
        return submitted;
    }

    public boolean processOneSynchronously() {
        Optional<GenerativeAiProvider> provider = providerSource.orderedStream().findFirst();
        if (provider.isEmpty()) {
            return false;
        }
        Optional<UUID> claimed = claimService.claimNext(workerId);
        claimed.ifPresent(id -> process(id, provider.orElseThrow()));
        return claimed.isPresent();
    }

    private void process(UUID analysisId, GenerativeAiProvider provider) {
        AnalysisAttemptService.AttemptContext attempt;
        try {
            Optional<AnalysisAttemptService.AttemptContext> prepared =
                    attemptService.prepare(analysisId, workerId, provider);
            if (prepared.isEmpty()) {
                return;
            }
            attempt = prepared.orElseThrow();
        } catch (AnalysisLeaseLostException exception) {
            return;
        } catch (RuntimeException exception) {
            terminalPreparationFailure(analysisId);
            return;
        }

        try {
            GenerativeAiResponse response = provider.generate(attempt.request());
            validator.validate(attempt.request().output(), response.structuredOutput(), provider.providerId());
            AnalysisResult result = objectMapper.treeToValue(response.structuredOutput(), AnalysisResult.class);
            attemptService.succeed(analysisId, attempt.invocationId(), workerId, response, result);
        } catch (GenerativeAiException exception) {
            completeProviderFailure(analysisId, attempt, exception);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            GenerativeAiException invalid = new GenerativeAiException(
                    GenerativeAiErrorType.INVALID_RESPONSE, provider.providerId(), false,
                    "The generative AI provider returned an invalid response", exception);
            completeProviderFailure(analysisId, attempt, invalid);
        } catch (AnalysisLeaseLostException exception) {
            // A later worker owns recovery; this invocation remains STARTED until that recovery.
        } catch (RuntimeException exception) {
            completeUnexpectedFailure(analysisId, attempt);
        }
    }

    private void completeProviderFailure(UUID analysisId, AnalysisAttemptService.AttemptContext attempt,
            GenerativeAiException exception) {
        FailureMapping mapping = map(exception.type());
        try {
            attemptService.fail(analysisId, attempt.invocationId(), attempt.attemptNumber(), workerId,
                    mapping.code(), mapping.status(), exception.retryable(), exception.getMessage());
        } catch (AnalysisLeaseLostException ignored) {
            // Recovery owns the stale invocation after lease expiry.
        }
    }

    private void completeUnexpectedFailure(UUID analysisId, AnalysisAttemptService.AttemptContext attempt) {
        try {
            attemptService.fail(analysisId, attempt.invocationId(), attempt.attemptNumber(), workerId,
                    AnalysisFailureCode.WORKER_PROCESSING_ERROR, AiInvocationStatus.FAILED, false,
                    "The analysis worker could not complete the attempt");
        } catch (AnalysisLeaseLostException ignored) {
            // Recovery owns the stale invocation after lease expiry.
        }
    }

    private void terminalPreparationFailure(UUID analysisId) {
        try {
            attemptService.failClaimedWithoutInvocation(analysisId, workerId);
        } catch (AnalysisLeaseLostException ignored) {
            return;
        } catch (RuntimeException exception) {
            log.error("Analysis preparation failure could not be persisted analysisId={}", analysisId);
        }
    }

    private static FailureMapping map(GenerativeAiErrorType type) {
        return switch (type) {
            case TIMEOUT -> new FailureMapping(AnalysisFailureCode.AI_TIMEOUT, AiInvocationStatus.TIMED_OUT);
            case RATE_LIMITED -> new FailureMapping(AnalysisFailureCode.AI_RATE_LIMITED, AiInvocationStatus.FAILED);
            case AUTHENTICATION -> new FailureMapping(AnalysisFailureCode.AI_AUTHENTICATION, AiInvocationStatus.FAILED);
            case INVALID_REQUEST -> new FailureMapping(AnalysisFailureCode.AI_INVALID_REQUEST, AiInvocationStatus.FAILED);
            case INVALID_RESPONSE -> new FailureMapping(
                    AnalysisFailureCode.AI_INVALID_RESPONSE, AiInvocationStatus.INVALID_RESPONSE);
            case PROVIDER_UNAVAILABLE -> new FailureMapping(
                    AnalysisFailureCode.AI_PROVIDER_UNAVAILABLE, AiInvocationStatus.FAILED);
            case PROVIDER_ERROR -> new FailureMapping(AnalysisFailureCode.AI_PROVIDER_ERROR, AiInvocationStatus.FAILED);
        };
    }

    private record FailureMapping(AnalysisFailureCode code, AiInvocationStatus status) {
    }
}
