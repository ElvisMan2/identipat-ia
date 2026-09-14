package com.mnk.identipatia.ai.model;

public record ProviderExecution(String provider, String model, String providerRequestId) {

    public ProviderExecution {
        AiModelValidation.notBlank(provider, "provider");
        AiModelValidation.notBlank(model, "model");
        if (providerRequestId != null && providerRequestId.isBlank()) {
            throw new IllegalArgumentException("providerRequestId must be null or non-blank");
        }
    }
}
