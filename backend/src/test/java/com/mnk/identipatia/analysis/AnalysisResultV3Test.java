package com.mnk.identipatia.analysis;

import com.mnk.identipatia.analysis.result.v2.Article15Screening;
import com.mnk.identipatia.analysis.result.v2.Article20Screening;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAccess;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAssessment;
import com.mnk.identipatia.analysis.result.v2.PatentScreening;
import com.mnk.identipatia.analysis.result.v2.ScreeningAssessment;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.mnk.identipatia.analysis.result.v3.TraditionalKnowledgeAccess;
import com.mnk.identipatia.analysis.result.v3.TraditionalKnowledgeAssessment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultV3Test {

    @Test
    void createsDefensiveResultAndRedactsSensitiveContent() {
        List<String> missing = new ArrayList<>(List.of("Finalidad de uso"));
        TraditionalKnowledgeAccess access = access(
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED, missing);
        AnalysisResultV3 result = result(access);

        missing.add("mutación externa");
        assertThat(access.missingInformation()).containsExactly("Finalidad de uso");
        assertThatThrownBy(() -> access.missingInformation().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.toString()).contains("<redacted>").doesNotContain("Resumen sensible");
    }

    @Test
    void enforcesTraditionalKnowledgeStateInvariants() {
        assertThatThrownBy(() -> access(TraditionalKnowledgeAssessment.INSUFFICIENT_INFORMATION, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missingInformation");
        assertThatThrownBy(() -> access(TraditionalKnowledgeAssessment.NOT_INDICATED,
                List.of("dato improcedente")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
        assertThat(access(TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED, List.of())).isNotNull();
        assertThat(access(TraditionalKnowledgeAssessment.INSUFFICIENT_INFORMATION,
                List.of("Vínculo con Perú"))).isNotNull();
    }

    @Test
    void rejectsNullBlankAndInvalidCollections() {
        assertThatThrownBy(() -> new TraditionalKnowledgeAccess(null, "Razón", List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TraditionalKnowledgeAccess(
                TraditionalKnowledgeAssessment.NOT_INDICATED, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraditionalKnowledgeAccess(
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED, "Razón", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TraditionalKnowledgeAccess(
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED, "Razón",
                java.util.Arrays.asList("válido", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisResultV3(
                AnalysisResultV3.SCHEMA_VERSION, "Resumen", List.of(), screening(), genetic(), null,
                List.of(), List.of())).isInstanceOf(NullPointerException.class);
    }

    private AnalysisResultV3 result(TraditionalKnowledgeAccess access) {
        return new AnalysisResultV3(AnalysisResultV3.SCHEMA_VERSION, "Resumen sensible", List.of(),
                screening(), genetic(), access, List.of(), List.of());
    }

    private TraditionalKnowledgeAccess access(
            TraditionalKnowledgeAssessment assessment, List<String> missingInformation) {
        return new TraditionalKnowledgeAccess(assessment, "Evaluación sustentada.", missingInformation);
    }

    private PatentScreening screening() {
        return new PatentScreening(false,
                new Article15Screening(ScreeningAssessment.NO_POTENTIAL_MATCH, "No hay indicios.", List.of()),
                new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH, "No hay indicios.", List.of()));
    }

    private GeneticResourceAccess genetic() {
        return new GeneticResourceAccess(GeneticResourceAssessment.NOT_INDICATED, "No hay indicios.", List.of());
    }
}
