package com.mnk.identipatia.ai.model;

public record PromptReference(String promptId, String version) {

    public PromptReference {
        AiModelValidation.resourcePart(promptId, "promptId");
        AiModelValidation.resourcePart(version, "version");
    }
}
