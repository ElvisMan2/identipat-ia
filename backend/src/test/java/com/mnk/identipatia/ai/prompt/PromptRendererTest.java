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
    void loadsCitizenLanguagePromptAndKeepsV3BaselineAvailable() {
        PromptRenderer renderer = new PromptRenderer(new PromptRegistry());
        PromptReference baselineReference = new PromptReference("intellectual-property-analysis", "0.3");
        PromptReference activeReference = new PromptReference("intellectual-property-analysis", "0.5.1");

        RenderedPrompt baseline = renderer.render(baselineReference, Map.of("USER_DESCRIPTION", "caso sintético"));
        RenderedPrompt active = renderer.render(activeReference, Map.of("USER_DESCRIPTION", "caso sintético"));
        String activeSystem = active.messages().getFirst().content();
        String activeUser = active.messages().getLast().content();

        assertThat(baseline.reference()).isEqualTo(baselineReference);
        assertThat(baseline.messages().getFirst().content()).contains("screening limitado");
        assertThat(active.reference()).isEqualTo(activeReference);
        assertThat(activeSystem)
                .contains("español natural, claro y profesional")
                .contains("No uses la palabra inglesa `screening` en esos campos")
                .contains("«revisión preliminar»", "«evaluación de posibles exclusiones»",
                        "«posibles limitaciones para la protección mediante patente»")
                .contains("No afirmes que una patente será concedida, rechazada o definitivamente excluida")
                .contains("No confundas la referencia general del artículo 14")
                .contains("artículos 15 y 20", "evita repetir mecánicamente los artículos")
                .contains("Estas reglas son editoriales")
                .contains("summary", "rationale", "observations", "warnings");
        assertThat(activeUser).contains("evaluación preliminar y limitada")
                .doesNotContainIgnoringCase("screening");
    }

    @Test
    void geneticResourcePromptsRenderForTheirInputsAndKeepHistoricalVersionsAvailable() {
        PromptRenderer renderer = new PromptRenderer(new PromptRegistry());
        PromptReference historicalText = new PromptReference("intellectual-property-analysis", "0.3.1");
        PromptReference historicalPdf = new PromptReference("intellectual-property-analysis", "0.3.2");
        PromptReference currentText = new PromptReference("intellectual-property-analysis", "0.4.1");
        PromptReference currentPdf = new PromptReference("intellectual-property-analysis", "0.4");

        RenderedPrompt oldText = renderer.render(historicalText, Map.of("USER_DESCRIPTION", "caso sintético"));
        RenderedPrompt oldPdf = renderer.render(historicalPdf, Map.of());
        RenderedPrompt text = renderer.render(currentText, Map.of("USER_DESCRIPTION", "caso sintético"));
        RenderedPrompt pdf = renderer.render(currentPdf, Map.of());

        assertThat(text.messages().getLast().content()).contains("caso sintético").doesNotContain("{{");
        assertThat(pdf.messages().getLast().content())
                .contains("documento PDF adjunto", "elementos visuales")
                .doesNotContain("{{");
        assertThat(section5(text.messages().getFirst().content()))
                .isEqualTo(section5(pdf.messages().getFirst().content()))
                .contains("Finalidad orientativa", "POTENTIALLY_REQUIRED", "INSUFFICIENT_INFORMATION",
                        "NOT_INDICATED", "missingInformation", "conocimientos colectivos")
                .contains("No utilices este estado únicamente porque faltan datos complementarios")
                .contains("La presencia de un recurso biológico no demuestra por sí misma");
        assertThat(text.templateHash()).matches("[0-9a-f]{64}").isNotEqualTo(oldText.templateHash());
        assertThat(pdf.templateHash()).matches("[0-9a-f]{64}").isNotEqualTo(oldPdf.templateHash());
        assertThat(text.renderedHash()).matches("[0-9a-f]{64}");
        assertThat(pdf.renderedHash()).matches("[0-9a-f]{64}");
        assertThat(text.renderedSnapshot()).contains("caso sintético");
        assertThat(pdf.renderedSnapshot()).contains("documento PDF adjunto");
    }

    @Test
    void activeTextAndPdfPromptsLoadAndRender() {
        PromptRenderer renderer = new PromptRenderer(new PromptRegistry());
        PromptReference textReference = new PromptReference("intellectual-property-analysis", "0.5.1");
        PromptReference pdfReference = new PromptReference("intellectual-property-analysis", "0.5");

        RenderedPrompt text = renderer.render(textReference, Map.of("USER_DESCRIPTION", "caso sintético"));
        RenderedPrompt pdf = renderer.render(pdfReference, Map.of());
        assertThat(text.reference()).isEqualTo(textReference);
        assertThat(pdf.reference()).isEqualTo(pdfReference);
        assertThat(text.messages().getLast().content()).contains("caso sintético").doesNotContain("{{");
        assertThat(pdf.messages().getLast().content())
                .contains("documento PDF adjunto", "elementos visuales")
                .doesNotContain("{{");
        assertThat(text.templateHash()).matches("[0-9a-f]{64}");
        assertThat(pdf.templateHash()).matches("[0-9a-f]{64}");
        assertThat(text.renderedHash()).matches("[0-9a-f]{64}");
        assertThat(pdf.renderedHash()).matches("[0-9a-f]{64}");
        assertThat(text.renderedSnapshot()).contains("caso sintético");
        assertThat(pdf.renderedSnapshot()).contains("documento PDF adjunto");
    }

    private static String section5(String system) {
        return system.substring(system.indexOf("## 5. Recursos genéticos"),
                system.indexOf("## 6. Acceso a conocimientos colectivos"));
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
