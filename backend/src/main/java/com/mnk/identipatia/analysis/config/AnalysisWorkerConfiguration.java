package com.mnk.identipatia.analysis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AnalysisProperties.class)
public class AnalysisWorkerConfiguration {

    @Bean
    String analysisWorkerId() {
        return "analysis-worker-" + UUID.randomUUID();
    }

    @Bean
    Long analysisPollIntervalMillis(AnalysisProperties properties) {
        return properties.pollInterval().toMillis();
    }

    @Bean(name = "analysisTaskExecutor")
    ThreadPoolTaskExecutor analysisTaskExecutor(AnalysisProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.workerQueueCapacity());
        executor.setThreadNamePrefix("analysis-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
