package com.mnk.identipatia.ai.model;

import java.util.Objects;

public record AiMessage(AiMessageRole role, String content) {

    public AiMessage {
        Objects.requireNonNull(role, "role must not be null");
        AiModelValidation.notBlank(content, "content");
    }
}
