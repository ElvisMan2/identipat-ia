package com.mnk.identipatia.analysis.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisWorkerRejectionTest {
    @Mock AnalysisClaimService claimService;
    @Mock AnalysisAttemptService attemptService;
    @Mock ObjectProvider<GenerativeAiProvider> providerSource;
    @Mock GenerativeAiProvider provider;
    @Mock ThreadPoolTaskExecutor executor;
    @Mock ThreadPoolExecutor threadPoolExecutor;
    @Mock BlockingQueue<Runnable> queue;

    @Test
    void executorRejectionImmediatelyReleasesOnlyTheOwnedLease() {
        AnalysisProperties properties = new AnalysisProperties(
                20, 20_000, true, Duration.ofSeconds(2), 1, 0,
                Duration.ofSeconds(120), 2, Duration.ofSeconds(5), 4000);
        UUID analysisId = UUID.randomUUID();
        when(providerSource.orderedStream()).thenReturn(Stream.of(provider));
        when(executor.getActiveCount()).thenReturn(0);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);
        when(queue.remainingCapacity()).thenReturn(0);
        when(claimService.claimNext("worker-test")).thenReturn(Optional.of(analysisId));
        org.mockito.Mockito.doThrow(new TaskRejectedException("saturated"))
                .when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        AnalysisWorker worker = new AnalysisWorker(properties, claimService, attemptService, providerSource,
                new StructuredOutputValidator(), new ObjectMapper(), executor, "worker-test");

        assertThat(worker.pollOnce()).isZero();
        verify(claimService).release(analysisId, "worker-test");
    }
}
