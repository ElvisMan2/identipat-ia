package com.mnk.identipatia.ai.prompt;

import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.RenderedPrompt;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRendererTest {

    private static final PromptReference SMOKE = new PromptReference("provider-smoke-test", "1.0");

    @Test
    void loadsUtf8ClasspathPromptAndRendersAllVariables() {
        RenderedPrompt rendered = new PromptRenderer(new PromptRegistry())
                .render(SMOKE, Map.of("TEST_LABEL", "integración-ñ"));

        assertThat(rendered.messages()).hasSize(2);
        assertThat(rendered.messages().get(1).content()).contains("integración-ñ").doesNotContain("{{");
        assertThat(rendered.templateHash()).matches("[0-9a-f]{64}");
        assertThat(rendered.renderedHash()).matches("[0-9a-f]{64}");
        assertThat(rendered.renderedSnapshot()).startsWith("[SYSTEM]\n").contains("\n\n[USER]\n");
    }

    @Test
    void renderingIsDeterministicAndNormalizesCrLfInTemplatesAndValues() {
        PromptRenderer crlf = renderer("sistema\r\nlinea", "dato={{VALUE}}\rfin");
        PromptRenderer lf = renderer("sistema\nlinea", "dato={{VALUE}}\nfin");

        RenderedPrompt first = crlf.render(SMOKE, Map.of("VALUE", "uno\r\ndos"));
        RenderedPrompt second = lf.render(SMOKE, Map.of("VALUE", "uno\ndos"));

        assertThat(first.templateHash()).isEqualTo(second.templateHash());
        assertThat(first.renderedHash()).isEqualTo(second.renderedHash());
        assertThat(first.renderedSnapshot()).isEqualTo(second.renderedSnapshot()).doesNotContain("\r");
    }

    @Test
    void rejectsMissingInvalidMalformedAndUnusedVariables() {
        assertThatThrownBy(() -> renderer("system", "{{REQUIRED}}")
                .render(SMOKE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unresolved");
        assertThatThrownBy(() -> renderer("system", "{{invalid}}")
                .render(SMOKE, Map.of("invalid", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderer("system", "{{BROKEN")
                .render(SMOKE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderer("system", "user")
                .render(SMOKE, Map.of("EXTRA", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unused");
    }

    @Test
    void reportsMissingPromptWithoutExposingAPath() {
        assertThatThrownBy(() -> new PromptRenderer(new PromptRegistry())
                .render(new PromptReference("missing", "1.0"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt not found")
                .hasMessageNotContaining("classpath");
    }

    private PromptRenderer renderer(String system, String user) {
        return new PromptRenderer(new PromptRegistry() {
            @Override
            public PromptTemplates load(PromptReference ignored) {
                return new PromptTemplates(system, user);
            }
        });
    }
}
