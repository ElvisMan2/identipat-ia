package com.mnk.identipatia.ai.prompt;

import java.util.Objects;

public record PromptTemplates(String systemTemplate, String userTemplate) {

    public PromptTemplates {
        Objects.requireNonNull(systemTemplate, "systemTemplate must not be null");
        Objects.requireNonNull(userTemplate, "userTemplate must not be null");
    }
}
