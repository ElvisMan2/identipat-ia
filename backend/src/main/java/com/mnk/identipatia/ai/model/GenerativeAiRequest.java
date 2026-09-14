package com.mnk.identipatia.ai.model;

import java.util.Objects;

public record GenerativeAiRequest(
        RenderedPrompt prompt,
        StructuredOutputDefinition output,
        GenerationOptions options) {

    public GenerativeAiRequest {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(output, "output must not be null");
        options = options == null ? GenerationOptions.defaults() : options;
    }
}
