package com.mnk.identipatia.analysis.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_invocations")
@Getter
@Setter
@NoArgsConstructor
public class AiInvocation {
    @Id
    @Column(name = "invocation_id", nullable = false)
    private UUID invocationId;
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiInvocationStatus status;
    @Column(length = 64)
    private String provider;
    @Column(length = 255)
    private String model;
    @Column(name = "provider_request_id", length = 255)
    private String providerRequestId;
    @Column(name = "prompt_id", nullable = false, length = 128)
    private String promptId;
    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;
    @Column(name = "template_hash", nullable = false, length = 64)
    private String templateHash;
    @Column(name = "rendered_hash", nullable = false, length = 64)
    private String renderedHash;
    @Column(name = "rendered_prompt_snapshot", nullable = false, columnDefinition = "text")
    private String renderedPromptSnapshot;
    @Column(name = "output_schema_id", nullable = false, length = 128)
    private String outputSchemaId;
    @Column(name = "output_schema_version", nullable = false, length = 64)
    private String outputSchemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_parameters", columnDefinition = "jsonb")
    private JsonNode requestParameters;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_provider_response", columnDefinition = "jsonb")
    private JsonNode rawProviderResponse;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_provider_response", columnDefinition = "jsonb")
    private JsonNode structuredProviderResponse;
    @Column(name = "input_tokens")
    private Long inputTokens;
    @Column(name = "output_tokens")
    private Long outputTokens;
    @Column(name = "total_tokens")
    private Long totalTokens;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "jsonb")
    private JsonNode tokenUsage;
    @Column(name = "latency_ms")
    private Long latencyMs;
    @Column(name = "finish_reason", length = 255)
    private String finishReason;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 512)
    private String errorMessage;
    private Boolean retryable;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
}
