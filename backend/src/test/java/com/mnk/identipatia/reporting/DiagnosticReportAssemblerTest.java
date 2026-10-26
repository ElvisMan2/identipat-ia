package com.mnk.identipatia.reporting;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.PatentabilityAssessment;
import com.mnk.identipatia.analysis.result.PatentabilityOutcome;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.Article15Literal;
import com.mnk.identipatia.analysis.result.v2.Article15Match;
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
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.dto.DisclaimerUpsertRequest;
import com.mnk.identipatia.reporting.dto.ReportDisclaimerResponse;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.model.ReportDisclaimerSource;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;
import com.mnk.identipatia.reporting.service.DiagnosticReportAssemblerV1;
import com.mnk.identipatia.reporting.service.DiagnosticReportAssemblerV2;
import com.mnk.identipatia.reporting.service.DiagnosticReportPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticReportAssemblerTest {
    private final DiagnosticReportAssemblerV2 assemblerV2 = new DiagnosticReportAssemblerV2();

    @Test
    void v2MapsAllSevenLabelsCitizenApplicabilityLegalBasisAndSharedScreening() {
        AnalysisResultV2 result = result(true, true,
                Arrays.stream(ProtectionType.values()).map(this::option).toList());

        DiagnosticReport report = assemblerV2.assemble(analysis(), result, context(), disclaimer());

        assertThat(report.protectionOptions()).extracting(DiagnosticReport.ProtectionPresentation::name)
                .containsExactly("Patente de invención", "Modelo de utilidad", "Diseño industrial",
                        "Derecho de autor", "Signo distintivo", "Certificado de obtentor", "Secreto empresarial");
        assertThat(report.protectionOptions()).extracting(DiagnosticReport.ProtectionPresentation::applicabilityLabel)
                .containsOnly("Se relaciona claramente");
        assertThat(report.protectionOptions().getFirst().legalBasis().getFirst().instrument())
                .isEqualTo("Decisión 486");
        assertThat(report.protectionOptions()).allMatch(option -> option.patentScreening() == null);
        assertThat(report.patentScreening()).isNotNull();
        assertThat(report.patentScreening().title()).isEqualTo("Revisión de la vía patentaria");
        assertThat(report.patentScreening().context()).contains("modalidades técnicas identificadas");
        assertThat(screeningCount(report)).isEqualTo(1);
        assertThat(report.observations()).containsExactly("Aspecto específico");
        assertThat(report.warnings()).containsExactly("Advertencia específica");
        assertThat(report.scope()).contains("No determina registrabilidad");
    }

    @Test
    void screeningWithOnlyInventionPatentIsNestedExactlyOnce() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(true, true, List.of(option(ProtectionType.INVENTION_PATENT))), context(), disclaimer());

        assertThat(report.protectionOptions().getFirst().patentScreening()).isNotNull();
        assertThat(report.protectionOptions().getFirst().patentScreening().title())
                .isEqualTo("Revisión específica de la vía de patente de invención");
        assertThat(report.protectionOptions().getFirst().patentScreening().context())
                .contains("artículo 14").contains("artículos 15 y 20");
        assertThat(report.patentScreening()).isNull();
        assertThat(screeningCount(report)).isEqualTo(1);
    }

    @Test
    void screeningWithOnlyUtilityModelIsNestedExactlyOnceWithNeutralContext() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(true, false, List.of(option(ProtectionType.UTILITY_MODEL))), context(), disclaimer());

        assertThat(report.protectionOptions().getFirst().patentScreening()).isNotNull();
        assertThat(report.protectionOptions().getFirst().patentScreening().title())
                .isEqualTo("Revisión específica de la vía de modelo de utilidad");
        assertThat(report.protectionOptions().getFirst().patentScreening().context())
                .contains("artículos 15 y 20")
                .doesNotContain("artículo 14", "patente no aparece", "no se identificó una modalidad positiva");
        assertThat(report.patentScreening()).isNull();
        assertThat(screeningCount(report)).isEqualTo(1);
    }

    @Test
    void screeningWithPatentAndUtilityModelIsSharedExactlyOnce() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(true, true, List.of(option(ProtectionType.INVENTION_PATENT),
                        option(ProtectionType.UTILITY_MODEL))), context(), disclaimer());

        assertThat(report.protectionOptions()).allMatch(option -> option.patentScreening() == null);
        assertThat(report.patentScreening()).isNotNull();
        assertThat(report.patentScreening().title()).isEqualTo("Revisión de la vía patentaria");
        assertThat(report.patentScreening().context()).contains("modalidades técnicas identificadas");
        assertThat(screeningCount(report)).isEqualTo(1);
    }

    @Test
    void screeningWithoutPatentRouteUsesIndependentBlockWithoutInventingOption() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(true, false, List.of(option(ProtectionType.COPYRIGHT))), context(), disclaimer());

        assertThat(report.patentScreening()).isNotNull();
        assertThat(report.patentScreening().title()).isEqualTo("Revisión de la vía de patente");
        assertThat(report.patentScreening().context())
                .contains("no se identificó una modalidad positiva de patente de invención o modelo de utilidad");
        assertThat(report.protectionOptions()).noneMatch(option -> option.type().equals("INVENTION_PATENT"));
        assertThat(report.protectionOptions()).allMatch(option -> option.patentScreening() == null);
        assertThat(screeningCount(report)).isEqualTo(1);
    }

    @Test
    void nonApplicableScreeningIsHiddenEverywhere() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(false, true, List.of(option(ProtectionType.INVENTION_PATENT))), context(), disclaimer());

        assertThat(report.patentScreening()).isNull();
        assertThat(report.protectionOptions().getFirst().patentScreening()).isNull();
        assertThat(screeningCount(report)).isZero();
    }

    @Test
    void v1IsAdaptedExplicitlyWithoutV2Fields() {
        var v1 = new AnalysisResultV1(AnalysisResultV1.SCHEMA_VERSION, "COPYRIGHT (POSSIBLE), resumen histórico",
                new PatentabilityAssessment(PatentabilityOutcome.INSUFFICIENT_INFORMATION,
                        "INVENTION_PATENT requiere revisión."),
                List.of(new com.mnk.identipatia.analysis.result.ProtectionOption(
                        com.mnk.identipatia.analysis.result.ProtectionType.COPYRIGHT,
                        com.mnk.identipatia.analysis.result.ProtectionApplicability.POSSIBLE,
                        "Se clasificó como POSSIBLE; COPYRIGHTABLE permanece.")),
                List.of("DISTINCTIVE_SIGN requiere contexto"), List.of("Relación UNLIKELY"));

        DiagnosticReport report = new DiagnosticReportAssemblerV1()
                .assemble(analysis(), v1, context(), disclaimer());

        assertThat(report.sourceResultVersion()).isEqualTo("analysis-result/1.0");
        assertThat(report.historicalPatentability().outcome()).isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(report.patentScreening()).isNull();
        assertThat(report.geneticResourceAccess()).isNull();
        assertThat(report.summary()).contains("derecho de autor — podría corresponder");
        assertThat(report.protectionOptions().getFirst().rationale())
                .contains("podría corresponder", "COPYRIGHTABLE").doesNotContain(" POSSIBLE");
        assertThat(report.historicalPatentability().rationale()).contains("patente de invención");
        assertThat(report.observations()).containsExactly("signo distintivo requiere contexto");
        assertThat(report.warnings()).containsExactly("Relación relación limitada");
    }

    @Test
    void summaryUsesCitizenLabelsOnlyForCompleteKnownTokensAndPreservesLegalText() {
        String legalText = "La Decisión 486, artículo 14, permanece sin cambios.";
        String source = "DISTINCTIVE_SIGN (LIKELY) y COPYRIGHT (POSSIBLE). "
                + legalText + " COPYRIGHTABLE, PRELIKELY y POSSIBLE_EFFECT no son tokens completos.";

        DiagnosticReport report = assemblerV2.assemble(analysis(),
                resultWithSummary(source, true, true,
                        List.of(option(ProtectionType.INVENTION_PATENT))), context(), disclaimer());

        assertThat(report.summary())
                .contains("signo distintivo — se relaciona claramente")
                .contains("derecho de autor — podría corresponder")
                .contains(legalText)
                .contains("COPYRIGHTABLE", "PRELIKELY", "POSSIBLE_EFFECT")
                .doesNotContain("DISTINCTIVE_SIGN", "COPYRIGHT (", "(LIKELY)", "(POSSIBLE)");
    }

    @Test
    void v2SanitizesEveryNarrativeFieldWithoutChangingPersistedResultOrPartialTokens() {
        String legalText = "La Decisión 486, artículo 14, permanece sin cambios.";
        ProtectionOption rawOption = new ProtectionOption(ProtectionType.INVENTION_PATENT,
                ProtectionApplicability.POSSIBLE,
                "Materia UTILITY_MODEL y POSSIBLE_EFFECT.",
                "Se clasifica como POSSIBLE. " + legalText + " COPYRIGHTABLE y PRELIKELY permanecen.",
                List.of(new LegalBasis(LegalInstrument.DECISION_486, List.of("14"))));
        AnalysisResultV2 raw = new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION,
                "INVENTION_PATENT (POSSIBLE)", List.of(rawOption),
                new PatentScreening(true,
                        new Article15Screening(ScreeningAssessment.POTENTIAL_MATCH,
                                "DISTINCTIVE_SIGN con evaluación LIKELY",
                                List.of(new Article15Match(Article15Literal.a,
                                        "COPYRIGHT y COPYRIGHTABLE"))),
                        new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                                "TRADE_SECRET sin coincidencia", List.of())),
                new GeneticResourceAccess(GeneticResourceAssessment.INSUFFICIENT_INFORMATION,
                        "INDUSTRIAL_DESIGN requiere datos", List.of("Dato para UTILITY_MODEL")),
                List.of("Observación DISTINCTIVE_SIGN y POSSIBLE_EFFECT"),
                List.of("Advertencia UNLIKELY y PRELIKELY"));

        DiagnosticReport report = assemblerV2.assemble(analysis(), raw, context(), disclaimer());
        DiagnosticReport.ProtectionPresentation option = report.protectionOptions().getFirst();
        DiagnosticReport.PatentScreeningPresentation screening = option.patentScreening();

        assertThat(report.summary()).isEqualTo("patente de invención — podría corresponder");
        assertThat(option.protectedSubjectMatter()).contains("modelo de utilidad", "POSSIBLE_EFFECT");
        assertThat(option.rationale()).contains("podría corresponder", legalText,
                "COPYRIGHTABLE", "PRELIKELY").doesNotContain(" como POSSIBLE");
        assertThat(screening.article15().rationale())
                .isEqualTo("signo distintivo con evaluación se relaciona claramente");
        assertThat(screening.article15().matches().getFirst().rationale())
                .isEqualTo("derecho de autor y COPYRIGHTABLE");
        assertThat(screening.article20().rationale()).isEqualTo("secreto empresarial sin coincidencia");
        assertThat(report.geneticResourceAccess().rationale()).isEqualTo("diseño industrial requiere datos");
        assertThat(report.geneticResourceAccess().missingInformation())
                .containsExactly("Dato para modelo de utilidad");
        assertThat(report.observations())
                .containsExactly("Observación signo distintivo y POSSIBLE_EFFECT");
        assertThat(report.warnings()).containsExactly("Advertencia relación limitada y PRELIKELY");
        assertThat(raw.protectionOptions().getFirst().rationale()).contains("POSSIBLE", "COPYRIGHTABLE");
    }

    @Test
    void pdfIsUnicodeSearchableAndMultipageWithoutTemporaryFiles() throws IOException {
        String summary = "DISTINCTIVE_SIGN (LIKELY) y COPYRIGHT (POSSIBLE). "
                + "Descripción técnica A → B con áéíóú, ñ, ¿pregunta? y ¡énfasis! ".repeat(18);
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                resultWithSummary(summary, true, true,
                        List.of(longPatentOption(), option(ProtectionType.COPYRIGHT))),
                context(), longDisclaimer());

        byte[] bytes = new DiagnosticReportPdfRenderer().render(report);

        assertThat(bytes).startsWith('%', 'P', 'D', 'F', '-');
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(text).contains("Reporte orientativo", "Patente de invención", "Importante",
                    report.analysisId().toString(), "áéíóú", "¿pregunta?", "¡énfasis!",
                    "A → B", "INICIO-DISCLAIMER", "FIN-DISCLAIMER",
                    "INICIO-MATERIA", "FIN-MATERIA", "INICIO-RAZON", "FIN-RAZON",
                    "Revisión específica de la vía de patente", "Artículo 15", "Artículo 20",
                    "Este screening se limita", "signo distintivo", "derecho de autor");
            assertThat(text)
                    .containsOnlyOnce("INICIO-MATERIA")
                    .containsOnlyOnce("FIN-MATERIA")
                    .containsOnlyOnce("INICIO-RAZON")
                    .containsOnlyOnce("FIN-RAZON")
                    .containsOnlyOnce("Este screening se limita")
                    .doesNotContain("DISTINCTIVE_SIGN", "COPYRIGHT (", "(LIKELY)", "(POSSIBLE)");
        }
    }

    @Test
    void shortGeneticResourcesBlockRemainsOnOnePage() throws IOException {
        AnalysisResultV2 base = resultWithSummary(
                "Contenido de relleno para aproximar el siguiente bloque al final de página. ".repeat(47),
                false, false, List.of(option(ProtectionType.COPYRIGHT)));
        AnalysisResultV2 result = new AnalysisResultV2(base.schemaVersion(), base.summary(),
                base.protectionOptions(), base.patentScreening(),
                new GeneticResourceAccess(GeneticResourceAssessment.INSUFFICIENT_INFORMATION,
                        "INICIO-GENETICO Rationale corto y completo. FIN-GENETICO",
                        List.of("INICIO-FALTANTE Origen del recurso. FIN-FALTANTE")),
                base.observations(), base.warnings());
        DiagnosticReport report = assemblerV2.assemble(analysis(), result, context(), disclaimer());

        try (var document = Loader.loadPDF(new DiagnosticReportPdfRenderer().render(report))) {
            List<String> pages = pageTexts(document);
            String geneticPage = pages.stream().filter(page -> page.contains("Recursos genéticos"))
                    .findFirst().orElseThrow();
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(geneticPage).contains("INICIO-GENETICO", "FIN-GENETICO",
                    "INICIO-FALTANTE", "FIN-FALTANTE");
            assertThat(pages).filteredOn(page -> page.contains("INICIO-GENETICO")
                    || page.contains("FIN-GENETICO") || page.contains("INICIO-FALTANTE")
                    || page.contains("FIN-FALTANTE")).hasSize(1);
        }
    }

    @Test
    void reportAndDisclaimerDtosRedactNarrativeContentFromToString() {
        DiagnosticReport report = assemblerV2.assemble(analysis(),
                result(true, true, List.of(option(ProtectionType.INVENTION_PATENT))), context(), disclaimer());
        ReportDisclaimerResponse response = new ReportDisclaimerResponse(UUID.randomUUID(), 3, "Título sensible",
                "Contenido sensible", ReportDisclaimerStatus.ACTIVE, ReportDisclaimerSource.ADMIN,
                Instant.now(), Instant.now(), Instant.now(), null);

        assertThat(report.toString()).contains("content=<redacted>")
                .doesNotContain("Resumen canónico", "Contenido institucional histórico");
        assertThat(response.toString()).contains("title=<redacted>", "content=<redacted>")
                .doesNotContain("Título sensible", "Contenido sensible");
        assertThat(new DisclaimerUpsertRequest("Título sensible", "Contenido sensible").toString())
                .doesNotContain("Título sensible", "Contenido sensible");
    }

    private ProtectionOption option(ProtectionType type) {
        LegalInstrument instrument = switch (type) {
            case PLANT_BREEDER_CERTIFICATE -> LegalInstrument.DECISION_345;
            case COPYRIGHT -> LegalInstrument.DECISION_351;
            default -> LegalInstrument.DECISION_486;
        };
        return new ProtectionOption(type, ProtectionApplicability.LIKELY, "Materia protegible",
                "Rationale persistido", List.of(new LegalBasis(instrument, List.of("14"))));
    }

    private ProtectionOption longPatentOption() {
        return new ProtectionOption(ProtectionType.INVENTION_PATENT, ProtectionApplicability.LIKELY,
                "INICIO-MATERIA " + "Componente técnico modular con transición A → B. ".repeat(34)
                        + "FIN-MATERIA",
                "INICIO-RAZON " + "Fundamento técnico persistido y verificable. ".repeat(55)
                        + "FIN-RAZON",
                List.of(new LegalBasis(LegalInstrument.DECISION_486, List.of("14"))));
    }

    private AnalysisResultV2 result(boolean applicable, boolean patentPresent, List<ProtectionOption> options) {
        List<ProtectionOption> normalized = patentPresent ? options
                : options.stream().filter(option -> option.type() != ProtectionType.INVENTION_PATENT).toList();
        ScreeningAssessment assessment = applicable ? ScreeningAssessment.POTENTIAL_MATCH
                : ScreeningAssessment.NO_POTENTIAL_MATCH;
        List<Article15Match> matches = applicable
                ? List.of(new Article15Match(Article15Literal.a, "Coincidencia real persistida")) : List.of();
        return new AnalysisResultV2(AnalysisResultV2.SCHEMA_VERSION, "Resumen canónico", normalized,
                new PatentScreening(applicable,
                        new Article15Screening(assessment, "Evaluación del artículo 15", matches),
                        new Article20Screening(ScreeningAssessment.NO_POTENTIAL_MATCH,
                                "Evaluación del artículo 20", List.of())),
                new GeneticResourceAccess(GeneticResourceAssessment.INSUFFICIENT_INFORMATION,
                        "No hay datos suficientes", List.of("Origen del recurso")),
                List.of("Aspecto específico"), List.of("Advertencia específica"));
    }

    private AnalysisResultV2 resultWithSummary(String summary, boolean applicable, boolean patentPresent,
            List<ProtectionOption> options) {
        AnalysisResultV2 base = result(applicable, patentPresent, options);
        return new AnalysisResultV2(base.schemaVersion(), summary, base.protectionOptions(),
                base.patentScreening(), base.geneticResourceAccess(), base.observations(), base.warnings());
    }

    private static Analysis analysis() {
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(UUID.randomUUID());
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setCompletedAt(Instant.parse("2026-10-25T12:00:00Z"));
        return analysis;
    }

    private static AnalysisReportContext context() {
        AnalysisReportContext context = new AnalysisReportContext();
        context.setReportVersion("diagnostic-report/1.0");
        return context;
    }

    private static ReportDisclaimer disclaimer() {
        ReportDisclaimer disclaimer = new ReportDisclaimer();
        disclaimer.setDisclaimerId(UUID.randomUUID());
        disclaimer.setVersion(3L);
        disclaimer.setTitle("Importante");
        disclaimer.setContent("Contenido institucional histórico.");
        return disclaimer;
    }

    private static ReportDisclaimer longDisclaimer() {
        ReportDisclaimer disclaimer = disclaimer();
        disclaimer.setContent("INICIO-DISCLAIMER "
                + "Contenido institucional extenso con caracteres áéíóú y ñ. ".repeat(75)
                + " FIN-DISCLAIMER");
        return disclaimer;
    }

    private static int screeningCount(DiagnosticReport report) {
        return (report.patentScreening() == null ? 0 : 1)
                + (int) report.protectionOptions().stream()
                        .filter(option -> option.patentScreening() != null).count();
    }

    private static List<String> pageTexts(org.apache.pdfbox.pdmodel.PDDocument document) throws IOException {
        List<String> pages = new java.util.ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(stripper.getText(document));
        }
        return pages;
    }
}
