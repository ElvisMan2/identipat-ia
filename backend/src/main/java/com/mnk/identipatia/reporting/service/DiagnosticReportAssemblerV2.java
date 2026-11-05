package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.Article15Match;
import com.mnk.identipatia.analysis.result.v2.Article20Match;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAssessment;
import com.mnk.identipatia.analysis.result.v2.LegalBasis;
import com.mnk.identipatia.analysis.result.v2.PatentScreening;
import com.mnk.identipatia.analysis.result.v2.ProtectionOption;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiagnosticReportAssemblerV2 implements DiagnosticReportAssembler<AnalysisResultV2> {
    private static final String SCREENING_TITLE = "Revisión de la vía patentaria";
    private static final String SCREENING_CONTEXT = "La descripción fue revisada respecto de posibles exclusiones "
            + "previstas en los artículos 15 y 20 de la Decisión 486.";

    @Override
    public DiagnosticReport assemble(Analysis analysis, AnalysisResultV2 result, AnalysisReportContext context,
            ReportDisclaimer disclaimer) {
        DiagnosticReportPresentation.requireSupportedContext(context);
        DiagnosticReport.PatentScreeningPresentation screening = result.patentScreening().applicable()
                ? screening(result.patentScreening()) : null;
        DiagnosticReport.GeneticResourcePresentation genetic =
                result.geneticResourceAccess().assessment() == GeneticResourceAssessment.NOT_INDICATED
                        ? null : genetic(result);
        List<DiagnosticReport.ProtectionPresentation> options = result.protectionOptions().stream()
                .map(DiagnosticReportAssemblerV2::option)
                .toList();

        return new DiagnosticReport(context.getReportVersion(), analysis.getAnalysisId(), result.schemaVersion(),
                analysis.getInputType(), analysis.getCompletedAt(), DiagnosticReportPresentation.TITLE,
                DiagnosticReportPresentation.narrative(result.summary()),
                DiagnosticReportPresentation.APPLICABILITY_NOTE, options, screening, genetic, null,
                DiagnosticReportPresentation.narratives(result.observations()),
                DiagnosticReportPresentation.narratives(result.warnings()),
                DiagnosticReportPresentation.SCOPE, DiagnosticReportPresentation.disclaimer(disclaimer));
    }

    private static DiagnosticReport.ProtectionPresentation option(ProtectionOption option) {
        return new DiagnosticReport.ProtectionPresentation(
                option.type().name(),
                DiagnosticReportPresentation.protectionLabel(option.type().name()),
                DiagnosticReportPresentation.RELATIONSHIP_QUESTION,
                option.applicability().name(),
                DiagnosticReportPresentation.applicabilityLabel(option.applicability().name()),
                DiagnosticReportPresentation.narrative(option.protectedSubjectMatter()),
                DiagnosticReportPresentation.narrative(option.rationale()),
                option.legalBasis().stream().map(DiagnosticReportAssemblerV2::legalBasis).toList(),
                DiagnosticReportPresentation.orientation(option.applicability().name()));
    }

    private static DiagnosticReport.GeneticResourcePresentation genetic(AnalysisResultV2 result) {
        var genetic = result.geneticResourceAccess();
        return new DiagnosticReport.GeneticResourcePresentation(
                genetic.assessment().name(), geneticLabel(genetic.assessment().name()),
                DiagnosticReportPresentation.narrative(genetic.rationale()),
                DiagnosticReportPresentation.narratives(genetic.missingInformation()));
    }

    private static DiagnosticReport.LegalBasisPresentation legalBasis(LegalBasis basis) {
        return new DiagnosticReport.LegalBasisPresentation(instrumentLabel(basis.instrument().name()),
                basis.articles());
    }

    private static String instrumentLabel(String instrument) {
        return switch (instrument) {
            case "DECISION_345" -> "Decisión 345";
            case "DECISION_351" -> "Decisión 351";
            case "DECISION_391" -> "Decisión 391";
            case "DECISION_486" -> "Decisión 486";
            default -> throw new IllegalArgumentException("Unsupported legal instrument: " + instrument);
        };
    }

    private static DiagnosticReport.PatentScreeningPresentation screening(PatentScreening screening) {
        return new DiagnosticReport.PatentScreeningPresentation(SCREENING_TITLE, SCREENING_CONTEXT,
                new DiagnosticReport.ScreeningArticlePresentation("Artículo 15",
                        screening.article15().assessment().name(),
                        screeningLabel(screening.article15().assessment().name()),
                        DiagnosticReportPresentation.narrative(screening.article15().rationale()),
                        screening.article15().matches().stream().map(DiagnosticReportAssemblerV2::match).toList()),
                new DiagnosticReport.ScreeningArticlePresentation("Artículo 20",
                        screening.article20().assessment().name(),
                        screeningLabel(screening.article20().assessment().name()),
                        DiagnosticReportPresentation.narrative(screening.article20().rationale()),
                        screening.article20().matches().stream().map(DiagnosticReportAssemblerV2::match).toList()),
                DiagnosticReportPresentation.SCREENING_NOTE);
    }

    private static DiagnosticReport.ScreeningMatchPresentation match(Article15Match match) {
        return new DiagnosticReport.ScreeningMatchPresentation("Literal " + match.literal().name(),
                DiagnosticReportPresentation.narrative(match.rationale()));
    }

    private static DiagnosticReport.ScreeningMatchPresentation match(Article20Match match) {
        return new DiagnosticReport.ScreeningMatchPresentation("Literal " + match.literal().name(),
                DiagnosticReportPresentation.narrative(match.rationale()));
    }

    private static String screeningLabel(String assessment) {
        return switch (assessment) {
            case "NO_POTENTIAL_MATCH" -> "No se identifican coincidencias potenciales";
            case "POTENTIAL_MATCH" -> "Se identifican coincidencias potenciales";
            case "INSUFFICIENT_INFORMATION" -> "Se necesita más información";
            default -> throw new IllegalArgumentException("Unsupported screening assessment: " + assessment);
        };
    }

    private static String geneticLabel(String assessment) {
        return switch (assessment) {
            case "NOT_INDICATED" -> "No se identifican indicios";
            case "POTENTIALLY_REQUIRED" -> "Requiere revisión especializada";
            case "INSUFFICIENT_INFORMATION" -> "Se necesita más información";
            default -> throw new IllegalArgumentException("Unsupported genetic resource assessment: " + assessment);
        };
    }
}
