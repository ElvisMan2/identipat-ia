package com.mnk.identipatia.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

public record TokenUsage(
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        JsonNode providerDetails) {

    public TokenUsage {
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(totalTokens, "totalTokens");
        providerDetails = providerDetails == null ? null : providerDetails.deepCopy();
    }

    @Override
    public JsonNode providerDetails() {
        return providerDetails == null ? null : providerDetails.deepCopy();
    }

    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative when present");
        }
    }
}
