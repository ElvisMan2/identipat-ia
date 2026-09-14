package com.mnk.identipatia.ai.model;

import java.util.List;
import java.util.Objects;

public record RenderedPrompt(
        PromptReference reference,
        List<AiMessage> messages,
        String templateHash,
        String renderedHash,
        String renderedSnapshot) {

    public RenderedPrompt {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        AiModelValidation.sha256(templateHash, "templateHash");
        AiModelValidation.sha256(renderedHash, "renderedHash");
        AiModelValidation.notBlank(renderedSnapshot, "renderedSnapshot");
    }
}
