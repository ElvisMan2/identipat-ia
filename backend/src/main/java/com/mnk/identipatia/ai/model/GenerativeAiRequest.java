package com.mnk.identipatia.ai.model;

import java.util.List;
import java.util.Objects;

public record GenerativeAiRequest(
        RenderedPrompt prompt,
        StructuredOutputDefinition output,
        GenerationOptions options,
        List<AiAttachment> attachments) {

    public GenerativeAiRequest(
            RenderedPrompt prompt,
            StructuredOutputDefinition output,
            GenerationOptions options) {
        this(prompt, output, options, List.of());
    }

    public GenerativeAiRequest {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(output, "output must not be null");
        options = options == null ? GenerationOptions.defaults() : options;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
