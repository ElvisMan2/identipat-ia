package com.mnk.identipatia.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record GenerativeAiResponse(
        ProviderExecution provider,
        JsonNode structuredOutput,
        JsonNode rawProviderResponse,
        TokenUsage tokenUsage,
        String finishReason,
        long latencyMs) {

    public GenerativeAiResponse {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(structuredOutput, "structuredOutput must not be null");
        Objects.requireNonNull(rawProviderResponse, "rawProviderResponse must not be null");
        Objects.requireNonNull(tokenUsage, "tokenUsage must not be null");
        AiModelValidation.notBlank(finishReason, "finishReason");
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be non-negative");
        }
        structuredOutput = structuredOutput.deepCopy();
        rawProviderResponse = rawProviderResponse.deepCopy();
    }

    @Override
    public JsonNode structuredOutput() {
        return structuredOutput.deepCopy();
    }

    @Override
    public JsonNode rawProviderResponse() {
        return rawProviderResponse.deepCopy();
    }
}
