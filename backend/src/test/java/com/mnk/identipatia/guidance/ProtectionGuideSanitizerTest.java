package com.mnk.identipatia.guidance;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.dto.AnalysisGuidanceResponse;
import com.mnk.identipatia.guidance.dto.ProtectionGuideCreateRequest;
import com.mnk.identipatia.guidance.service.ProtectionGuideSanitizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionGuideSanitizerTest {
    private final ProtectionGuideSanitizer sanitizer = new ProtectionGuideSanitizer();

    @Test
    void keepsOnlyPolicyElementsAndNormalizesHttpsLinks() {
        String sanitized = sanitizer.sanitize("""
                <h1>Título principal</h1><h2>Sección</h2><h3>Título</h3><h4>Recursos</h4>
                <div style="color: red; position: fixed">Texto <u>subrayado</u> <s>tachado</s></div>
                <p><strong>fuerte</strong> <em>énfasis</em><br /></p>
                <ul><li>Uno</li></ul><ol><li>Dos</li></ol><h4>Recursos</h4>
                <img src="https://www.indecopi.gob.pe/logo.png" alt="Logo">
                <a href="https://www.indecopi.gob.pe/">Enlace</a>
                """);

        assertThat(sanitized)
                .contains("<h1>Título principal</h1>", "<h2>Sección</h2>", "<h3>Título</h3>",
                        "<h4>Recursos</h4>", "<u>subrayado</u>", "<s>tachado</s>", "color",
                        "<img", "alt=\"Logo\"",
                        "<strong>fuerte</strong>", "<em>énfasis</em>",
                        "href=\"https://www.indecopi.gob.pe/\"", "target=\"_blank\"",
                        "rel=\"noopener noreferrer\"");
        assertThat(sanitizer.extractVisibleText(sanitized)).contains("Título", "Texto", "Enlace");
    }

    @Test
    void removesHostileTagsAttributesAndProtocolsIncludingMalformedHtml() {
        String sanitized = sanitizer.sanitize("""
                <p onclick="alert(1)">Seguro<script>alert(1)</script>
                <img src=x onerror=alert(1)><a href="javascript:alert(1)">js</a>
                <a href="data:text/html,boom">data</a><a href="file:///tmp/x">file</a>
                <iframe src="https://evil.example"></iframe><style>x{}</style><form><input></form>
                <svg onload="alert(1)"><circle></svg><strong>fin
                """);

        assertThat(sanitized).contains("Seguro", "js", "data", "file", "<strong>fin")
                .doesNotContain("script", "onclick", "onerror", "javascript:", "data:", "file:",
                        "iframe", "style", "form", "input", "svg", "img");
    }

    @Test
    void contentWithoutVisibleTextIsDetectable() {
        assertThat(sanitizer.hasVisibleText(sanitizer.sanitize("<script>alert(1)</script><br>"))).isFalse();
    }

    @Test
    void dtoToStringNeverExposesInstitutionalHtml() {
        String sensitiveHtml = "<p>contenido-institucional-completo</p>";
        var request = new ProtectionGuideCreateRequest(ProtectionType.COPYRIGHT,
                "Descripción institucional suficientemente extensa para validación.", sensitiveHtml);
        var response = new AnalysisGuidanceResponse("analysis-guidance/1.0", UUID.randomUUID(), Instant.EPOCH,
                List.of(new AnalysisGuidanceResponse.Modality("COPYRIGHT", "Derecho de autor", "LIKELY",
                        "Se relaciona claramente", 1, "Descripción", new AnalysisGuidanceResponse.NextSteps(
                                sensitiveHtml), Instant.EPOCH)));

        assertThat(request.toString()).doesNotContain(sensitiveHtml);
        assertThat(response.toString()).doesNotContain(sensitiveHtml);
        assertThat(response.modalities().getFirst().toString()).doesNotContain(sensitiveHtml);
        assertThat(response.modalities().getFirst().nextSteps().toString()).doesNotContain(sensitiveHtml);
    }
}
