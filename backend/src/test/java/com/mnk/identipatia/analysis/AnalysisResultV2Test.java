package com.mnk.identipatia.analysis;

import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.Article15Literal;
import com.mnk.identipatia.analysis.result.v2.Article15Match;
import com.mnk.identipatia.analysis.result.v2.Article15Screening;
import com.mnk.identipatia.analysis.result.v2.Article20Literal;
import com.mnk.identipatia.analysis.result.v2.Article20Match;
import com.mnk.identipatia.analysis.result.v2.Article20Screening;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAccess;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAssessment;
import com.mnk.identipatia.analysis.result.v2.LegalBasis;
import com.mnk.identipatia.analysis.result.v2.LegalInstrument;
import com.mnk.identipatia.analysis.result.v2.PatentScreening;
import com.mnk.identipatia.analysis.result.v2.ProtectionApplicability;
import com.mnk.identipatia.analysis.result.v2.ProtectionOption;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.analysis.result.v2.ScreeningAssessment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultV2Test {

    @Test
    void createsDefensiveCanonicalResultWithConcurrentProtectionOptions() {
        List<String> observations = new ArrayList<>(List.of("Falta precisar el uso."));
        AnalysisResultV2 result = new AnalysisResultV2(
                AnalysisResultV2.SCHEMA_VERSION,
                "Se identifican protecciones concurrentes.",
                List.of(option(ProtectionType.COPYRIGHT), option(ProtectionType.DISTINCTIVE_SIGN)),
                patentScreening(false, no15(), no20()),
                genetic(GeneticResourceAssessment.NOT_INDICATED, List.of()),
                observations,
                List.of());

        observations.add("mutación externa");
        assertThat(result.protectionOptions()).extracting(ProtectionOption::type)
                .containsExactly(ProtectionType.COPYRIGHT, ProtectionType.DISTINCTIVE_SIGN);
        assertThat(result.observations()).containsExactly("Falta precisar el uso.");
        assertThatThrownBy(() -> result.observations().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateProtectionTypesBlankTextAndInvalidCollections() {
        assertThatThrownBy(() -> result(List.of(option(ProtectionType.COPYRIGHT),
                option(ProtectionType.COPYRIGHT))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repeat");
        assertThatThrownBy(() -> new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION, " ", List.of(),
                patentScreening(false, no15(), no20()), genetic(GeneticResourceAssessment.NOT_INDICATED, List.of()),
                List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION, "Resumen", null,
                patentScreening(false, no15(), no20()), genetic(GeneticResourceAssessment.NOT_INDICATED, List.of()),
                List.of(), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION, "Resumen", List.of(),
                patentScreening(false, no15(), no20()), genetic(GeneticResourceAssessment.NOT_INDICATED, List.of()),
                java.util.Arrays.asList("válida", null), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProtectionOption(ProtectionType.COPYRIGHT,
                ProtectionApplicability.LIKELY, " ", "Razón", List.of(basis())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegalBasis(LegalInstrument.DECISION_351, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesArticle15And20AssessmentMatchInvariants() {
        assertThatThrownBy(() -> new Article15Screening(ScreeningAssessment.POTENTIAL_MATCH,
                "Hay indicios.", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Article15Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                "No hay indicios.", List.of(new Article15Match(Article15Literal.a, "Coincidencia."))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Article15Screening(ScreeningAssessment.INSUFFICIENT_INFORMATION,
                "Faltan datos.", List.of(new Article15Match(Article15Literal.b, "Indicio."))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Article20Screening(ScreeningAssessment.POTENTIAL_MATCH,
                "Hay indicios.", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                "No hay indicios.", List.of(new Article20Match(Article20Literal.d, "Coincidencia."))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Article20Screening(ScreeningAssessment.INSUFFICIENT_INFORMATION,
                "Faltan datos.", List.of(new Article20Match(Article20Literal.c, "Indicio."))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonApplicablePatentScreeningWithContradictoryAssessment() {
        Article15Screening potential = new Article15Screening(ScreeningAssessment.POTENTIAL_MATCH,
                "Software como tal.", List.of(new Article15Match(Article15Literal.e, "Se describe software.")));
        assertThatThrownBy(() -> patentScreening(false, potential, no20()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Non-applicable");
        Article20Screening insufficient = new Article20Screening(ScreeningAssessment.INSUFFICIENT_INFORMATION,
                "Faltan datos.", List.of());
        assertThatThrownBy(() -> patentScreening(false, no15(), insufficient))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Non-applicable");
    }

    @Test
    void insufficientGeneticResourceAssessmentRequiresMissingInformation() {
        assertThatThrownBy(() -> genetic(GeneticResourceAssessment.INSUFFICIENT_INFORMATION, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missingInformation");
        assertThat(genetic(GeneticResourceAssessment.INSUFFICIENT_INFORMATION,
                List.of("Origen del recurso biológico"))).isNotNull();
    }

    private AnalysisResultV2 result(List<ProtectionOption> options) {
        return new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION, "Resumen", options,
                patentScreening(false, no15(), no20()),
                genetic(GeneticResourceAssessment.NOT_INDICATED, List.of()), List.of(), List.of());
    }

    private ProtectionOption option(ProtectionType type) {
        return new ProtectionOption(type, ProtectionApplicability.POSSIBLE,
                "Elemento protegido", "Razón asociada a la descripción", List.of(basis()));
    }

    private LegalBasis basis() {
        return new LegalBasis(LegalInstrument.DECISION_351, List.of("1"));
    }

    private PatentScreening patentScreening(boolean applicable, Article15Screening article15,
            Article20Screening article20) {
        return new PatentScreening(applicable, article15, article20);
    }

    private Article15Screening no15() {
        return new Article15Screening(ScreeningAssessment.NO_POTENTIAL_MATCH, "No hay indicios.", List.of());
    }

    private Article20Screening no20() {
        return new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH, "No hay indicios.", List.of());
    }

    private GeneticResourceAccess genetic(GeneticResourceAssessment assessment, List<String> missing) {
        return new GeneticResourceAccess(assessment, "Evaluación sustentada.", missing);
    }
}
