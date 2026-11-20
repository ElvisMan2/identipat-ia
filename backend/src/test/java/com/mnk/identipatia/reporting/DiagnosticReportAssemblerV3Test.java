package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.result.v2.Article15Screening;
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
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.mnk.identipatia.analysis.result.v3.TraditionalKnowledgeAccess;
import com.mnk.identipatia.analysis.result.v3.TraditionalKnowledgeAssessment;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.service.DiagnosticReportAssemblerV3;
import com.mnk.identipatia.reporting.service.DiagnosticReportPdfRenderer;
import com.mnk.identipatia.reporting.service.OperationalDiagnosticReportTextRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticReportAssemblerV3Test {
    private final DiagnosticReportAssemblerV3 assembler = new DiagnosticReportAssemblerV3();

    @Test
    void activeTraditionalKnowledgeIsMappedWithControlledLabelAndSeparateOperationalSection() {
        DiagnosticReport report = assembler.assemble(analysis(), result(
                GeneticResourceAssessment.NOT_INDICATED,
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED,
                List.of("Finalidad exacta de utilización")), context("diagnostic-report/1.1"), disclaimer());

        assertThat(report.sourceResultVersion()).isEqualTo("analysis-result/3.0");
        assertThat(report.reportVersion()).isEqualTo("diagnostic-report/1.1");
        assertThat(report.traditionalKnowledgeAccess()).isNotNull();
        assertThat(report.traditionalKnowledgeAccess().assessmentLabel())
                .isEqualTo("Requiere revisión especializada");
        assertThat(report.traditionalKnowledgeAccess().missingInformation())
                .containsExactly("Finalidad exacta de utilización");
        assertThat(report.geneticResourceAccess()).isNull();

        String operational = new OperationalDiagnosticReportTextRenderer().render(report);
        assertThat(operational)
                .contains("Conocimientos colectivos de pueblos indígenas:",
                        "Requiere revisión especializada", "Finalidad exacta de utilización")
                .doesNotContain("Recursos genéticos:");
    }

    @Test
    void insufficientAndNotIndicatedFollowVisibilityPolicyAndNullIsOmittedFromJson() throws Exception {
        DiagnosticReport insufficient = assembler.assemble(analysis(), result(
                GeneticResourceAssessment.NOT_INDICATED,
                TraditionalKnowledgeAssessment.INSUFFICIENT_INFORMATION,
                List.of("Vínculo colectivo indígena")), context("diagnostic-report/1.1"), disclaimer());
        assertThat(insufficient.traditionalKnowledgeAccess().assessmentLabel())
                .isEqualTo("Se necesita más información");

        DiagnosticReport hidden = assembler.assemble(analysis(), result(
                GeneticResourceAssessment.NOT_INDICATED,
                TraditionalKnowledgeAssessment.NOT_INDICATED,
                List.of()), context("diagnostic-report/1.1"), disclaimer());
        assertThat(hidden.traditionalKnowledgeAccess()).isNull();
        assertThat(new ObjectMapper().findAndRegisterModules()
                .valueToTree(hidden).has("traditionalKnowledgeAccess")).isFalse();
    }

    @Test
    void pdfShowsGeneticAndTraditionalAlertsOnceInRequiredOrder() throws Exception {
        DiagnosticReport report = assembler.assemble(analysis(), result(
                GeneticResourceAssessment.POTENTIALLY_REQUIRED,
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED,
                List.of()), context("diagnostic-report/1.1"), disclaimer());

        try (var document = Loader.loadPDF(new DiagnosticReportPdfRenderer().render(report))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .containsOnlyOnce("Recursos genéticos")
                    .containsOnlyOnce("Acceso a conocimientos colectivos de pueblos indígenas")
                    .contains("Referencia general", "Ley N.º 27811");
            assertThat(text.indexOf("Recursos genéticos"))
                    .isLessThan(text.indexOf("Acceso a conocimientos colectivos de pueblos indígenas"));
            assertThat(text.indexOf("Acceso a conocimientos colectivos de pueblos indígenas"))
                    .isLessThan(text.indexOf("Aspectos a considerar"));
        }
    }

    @Test
    void v3KeepsRelationshipQuestionJsonFieldAndCitizenLabel() throws Exception {
        AnalysisResultV3 base = result(GeneticResourceAssessment.NOT_INDICATED,
                TraditionalKnowledgeAssessment.NOT_INDICATED, List.of());
        AnalysisResultV3 withOption = new AnalysisResultV3(base.schemaVersion(), base.summary(),
                List.of(new ProtectionOption(ProtectionType.COPYRIGHT, ProtectionApplicability.LIKELY,
                        "Expresión gráfica descrita", "Se describe una obra gráfica concreta.",
                        List.of(new LegalBasis(LegalInstrument.DECISION_351, List.of("4"))))),
                base.patentScreening(), base.geneticResourceAccess(), base.traditionalKnowledgeAccess(),
                base.observations(), base.warnings());

        DiagnosticReport report = assembler.assemble(analysis(), withOption,
                context("diagnostic-report/1.1"), disclaimer());

        assertThat(report.protectionOptions().getFirst().relationshipQuestion()).isEqualTo("Elementos Identificados");
        assertThat(new ObjectMapper().findAndRegisterModules().valueToTree(report)
                .at("/protectionOptions/0/relationshipQuestion").asText()).isEqualTo("Elementos Identificados");
        try (var document = Loader.loadPDF(new DiagnosticReportPdfRenderer().render(report))) {
            assertThat(new PDFTextStripper().getText(document)).contains("Elementos Identificados");
        }
    }

    @Test
    void preexistingV3ContextKeepsItsPersistedVersion10OnRead() {
        DiagnosticReport report = assembler.assemble(analysis(), result(
                GeneticResourceAssessment.NOT_INDICATED,
                TraditionalKnowledgeAssessment.POTENTIALLY_REQUIRED,
                List.of("Finalidad exacta")), context("diagnostic-report/1.0"), disclaimer());

        assertThat(report.reportVersion()).isEqualTo("diagnostic-report/1.0");
    }

    private static AnalysisResultV3 result(GeneticResourceAssessment geneticAssessment,
            TraditionalKnowledgeAssessment traditionalAssessment, List<String> missing) {
        List<String> geneticMissing = geneticAssessment == GeneticResourceAssessment.INSUFFICIENT_INFORMATION
                ? List.of("Origen") : List.of();
        return new AnalysisResultV3(AnalysisResultV3.SCHEMA_VERSION, "Resumen canónico", List.of(),
                new PatentScreening(false,
                        new Article15Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                                "Sin coincidencias.", List.of()),
                        new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                                "Sin coincidencias.", List.of())),
                new GeneticResourceAccess(geneticAssessment, "Evaluación genética separada.", geneticMissing),
                new TraditionalKnowledgeAccess(traditionalAssessment,
                        "Evaluación bajo la Ley N.º 27811.", missing),
                List.of(), List.of());
    }

    private static Analysis analysis() {
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(UUID.randomUUID());
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setCompletedAt(Instant.parse("2026-11-19T05:00:00Z"));
        return analysis;
    }

    private static AnalysisReportContext context(String version) {
        AnalysisReportContext context = new AnalysisReportContext();
        context.setReportVersion(version);
        return context;
    }

    private static ReportDisclaimer disclaimer() {
        ReportDisclaimer disclaimer = new ReportDisclaimer();
        disclaimer.setDisclaimerId(UUID.randomUUID());
        disclaimer.setVersion(1L);
        disclaimer.setTitle("Importante");
        disclaimer.setContent("Contenido institucional.");
        return disclaimer;
    }
}
