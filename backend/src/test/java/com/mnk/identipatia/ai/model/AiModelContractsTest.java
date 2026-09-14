package com.mnk.identipatia.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelContractsTest {

    private static final String HASH = "0".repeat(64);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void promptReferenceRejectsBlankAndTraversalParts() {
        assertThatThrownBy(() -> new PromptReference("", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromptReference("../secret", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromptReference("valid", ".."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiMessageRequiresRoleAndContent() {
        assertThatThrownBy(() -> new AiMessage(null, "content"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiMessage(AiMessageRole.USER, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generationOptionsValidateBoundsAndProvideExplicitDefaults() {
        assertThatThrownBy(() -> new GenerationOptions(0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationOptions(null, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationOptions(null, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(GenerationOptions.defaults())
                .isEqualTo(new GenerationOptions(null, null));
    }

    @Test
    void requestRequiresPromptAndOutputAndNormalizesNullOptions() throws Exception {
        StructuredOutputDefinition output = outputDefinition();
        assertThatThrownBy(() -> new GenerativeAiRequest(null, output, GenerationOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GenerativeAiRequest(renderedPrompt(), null, GenerationOptions.defaults()))
                .isInstanceOf(NullPointerException.class);

        GenerativeAiRequest request = new GenerativeAiRequest(renderedPrompt(), output, null);
        assertThat(request.options()).isEqualTo(GenerationOptions.defaults());
    }

    @Test
    void renderedPromptDefensivelyCopiesMessagesAndChecksHashes() {
        List<AiMessage> messages = new java.util.ArrayList<>();
        messages.add(new AiMessage(AiMessageRole.SYSTEM, "system"));
        RenderedPrompt prompt = new RenderedPrompt(
                new PromptReference("smoke", "1.0"), messages, HASH, HASH, "snapshot");
        messages.clear();

        assertThat(prompt.messages()).hasSize(1);
        assertThatThrownBy(() -> prompt.messages().add(new AiMessage(AiMessageRole.USER, "user")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new RenderedPrompt(
                new PromptReference("smoke", "1.0"), prompt.messages(), "invalid", HASH, "snapshot"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StructuredOutputDefinition outputDefinition() throws Exception {
        return new StructuredOutputDefinition(
                "result", "1.0", objectMapper.readTree("{\"type\":\"object\"}"), true);
    }

    private RenderedPrompt renderedPrompt() {
        return new RenderedPrompt(
                new PromptReference("smoke", "1.0"),
                List.of(new AiMessage(AiMessageRole.SYSTEM, "system")),
                HASH,
                HASH,
                "[SYSTEM]\nsystem");
    }
}
