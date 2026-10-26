package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.Article15Match;
import com.mnk.identipatia.analysis.result.v2.Article20Match;
import com.mnk.identipatia.analysis.result.v2.LegalBasis;
import com.mnk.identipatia.analysis.result.v2.PatentScreening;
import com.mnk.identipatia.analysis.result.v2.ProtectionOption;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class DiagnosticReportAssemblerV2 implements DiagnosticReportAssembler<AnalysisResultV2> {
    private static final Set<ProtectionType> PATENT_ROUTE_TYPES =
            EnumSet.of(ProtectionType.INVENTION_PATENT, ProtectionType.UTILITY_MODEL);

    @Override
    public DiagnosticReport assemble(Analysis analysis, AnalysisResultV2 result, AnalysisReportContext context,
            ReportDisclaimer disclaimer) {
        DiagnosticReportPresentation.requireSupportedContext(context);
        ScreeningPlacement placement = placement(result);
        DiagnosticReport.PatentScreeningPresentation screening = placement == ScreeningPlacement.HIDDEN
                ? null : screening(result.patentScreening(), placement);
        List<DiagnosticReport.ProtectionPresentation> options = result.protectionOptions().stream()
                .map(option -> option(option, nestedScreening(option.type(), placement, screening)))
                .toList();
        DiagnosticReport.PatentScreeningPresentation independentScreening = switch (placement) {
            case SHARED_PATENT_ROUTE, STANDALONE -> screening;
            default -> null;
        };

        var genetic = result.geneticResourceAccess();
        DiagnosticReport.GeneticResourcePresentation geneticPresentation =
                new DiagnosticReport.GeneticResourcePresentation(
                        genetic.assessment().name(), geneticLabel(genetic.assessment().name()),
                        DiagnosticReportPresentation.narrative(genetic.rationale()),
                        DiagnosticReportPresentation.narratives(genetic.missingInformation()));

        return new DiagnosticReport(context.getReportVersion(), analysis.getAnalysisId(), result.schemaVersion(),
                analysis.getInputType(), analysis.getCompletedAt(), DiagnosticReportPresentation.TITLE,
                DiagnosticReportPresentation.narrative(result.summary()),
                DiagnosticReportPresentation.APPLICABILITY_NOTE, options, independentScreening,
                geneticPresentation, null,
                DiagnosticReportPresentation.narratives(result.observations()),
                DiagnosticReportPresentation.narratives(result.warnings()),
                DiagnosticReportPresentation.SCOPE, DiagnosticReportPresentation.disclaimer(disclaimer));
    }

    private static DiagnosticReport.ProtectionPresentation option(ProtectionOption option,
            DiagnosticReport.PatentScreeningPresentation screening) {
        return new DiagnosticReport.ProtectionPresentation(
                option.type().name(),
                DiagnosticReportPresentation.protectionLabel(option.type().name()),
                DiagnosticReportPresentation.RELATIONSHIP_QUESTION,
                option.applicability().name(),
                DiagnosticReportPresentation.applicabilityLabel(option.applicability().name()),
                DiagnosticReportPresentation.narrative(option.protectedSubjectMatter()),
                DiagnosticReportPresentation.narrative(option.rationale()),
                option.legalBasis().stream().map(DiagnosticReportAssemblerV2::legalBasis).toList(),
                DiagnosticReportPresentation.orientation(option.applicability().name()), screening);
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

    private static DiagnosticReport.PatentScreeningPresentation screening(PatentScreening screening,
            ScreeningPlacement placement) {
        String title = switch (placement) {
            case INVENTION_PATENT -> "Revisión específica de la vía de patente de invención";
            case UTILITY_MODEL -> "Revisión específica de la vía de modelo de utilidad";
            case SHARED_PATENT_ROUTE -> "Revisión de la vía patentaria";
            case STANDALONE -> "Revisión de la vía de patente";
            case HIDDEN -> throw new IllegalArgumentException("Hidden screening has no presentation");
        };
        String context = switch (placement) {
            case INVENTION_PATENT -> "El artículo 14 constituye la base jurídica general de la modalidad. Los artículos 15 y 20 corresponden a una revisión limitada de posibles exclusiones.";
            case UTILITY_MODEL -> "Los artículos 15 y 20 corresponden a una revisión limitada de posibles exclusiones. La base jurídica de esta modalidad se muestra exclusivamente según el resultado del análisis.";
            case SHARED_PATENT_ROUTE -> "Las modalidades técnicas identificadas fueron revisadas también frente a las posibles exclusiones de los artículos 15 y 20 de la Decisión 486.";
            case STANDALONE -> "La descripción también fue revisada respecto de la vía patentaria, aunque no se identificó una modalidad positiva de patente de invención o modelo de utilidad.";
            case HIDDEN -> throw new IllegalArgumentException("Hidden screening has no presentation");
        };
        return new DiagnosticReport.PatentScreeningPresentation(title, context,
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

    private static ScreeningPlacement placement(AnalysisResultV2 result) {
        if (!result.patentScreening().applicable()) {
            return ScreeningPlacement.HIDDEN;
        }
        Set<ProtectionType> present = result.protectionOptions().stream()
                .map(ProtectionOption::type)
                .filter(PATENT_ROUTE_TYPES::contains)
                .collect(java.util.stream.Collectors.toSet());
        if (present.size() == 2) {
            return ScreeningPlacement.SHARED_PATENT_ROUTE;
        }
        if (present.contains(ProtectionType.INVENTION_PATENT)) {
            return ScreeningPlacement.INVENTION_PATENT;
        }
        if (present.contains(ProtectionType.UTILITY_MODEL)) {
            return ScreeningPlacement.UTILITY_MODEL;
        }
        return ScreeningPlacement.STANDALONE;
    }

    private static DiagnosticReport.PatentScreeningPresentation nestedScreening(ProtectionType type,
            ScreeningPlacement placement, DiagnosticReport.PatentScreeningPresentation screening) {
        return switch (placement) {
            case INVENTION_PATENT -> type == ProtectionType.INVENTION_PATENT ? screening : null;
            case UTILITY_MODEL -> type == ProtectionType.UTILITY_MODEL ? screening : null;
            default -> null;
        };
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

    private enum ScreeningPlacement {
        INVENTION_PATENT,
        UTILITY_MODEL,
        SHARED_PATENT_ROUTE,
        STANDALONE,
        HIDDEN
    }
}
