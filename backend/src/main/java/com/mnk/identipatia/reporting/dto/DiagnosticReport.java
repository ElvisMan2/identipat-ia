package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagnosticReport(
        String reportVersion,
        UUID analysisId,
        String sourceResultVersion,
        AnalysisInputType inputType,
        Instant analysisCompletedAt,
        String title,
        String summary,
        String applicabilityNote,
        List<ProtectionPresentation> protectionOptions,
        PatentScreeningPresentation patentScreening,
        GeneticResourcePresentation geneticResourceAccess,
        HistoricalPatentabilityPresentation historicalPatentability,
        List<String> observations,
        List<String> warnings,
        String scope,
        DisclaimerPresentation disclaimer) {

    @Override
    public String toString() {
        return "DiagnosticReport[reportVersion=" + reportVersion
                + ", analysisId=" + analysisId
                + ", sourceResultVersion=" + sourceResultVersion
                + ", inputType=" + inputType
                + ", content=<redacted>]";
    }

    public record ProtectionPresentation(
            String type,
            String name,
            String relationshipQuestion,
            String applicability,
            String applicabilityLabel,
            String protectedSubjectMatter,
            String rationale,
            List<LegalBasisPresentation> legalBasis,
            String orientation,
            PatentScreeningPresentation patentScreening) {
    }

    public record LegalBasisPresentation(String instrument, List<String> articles) {
    }

    public record PatentScreeningPresentation(
            String title,
            String context,
            ScreeningArticlePresentation article15,
            ScreeningArticlePresentation article20,
            String scopeNote) {
    }

    public record ScreeningArticlePresentation(
            String article,
            String assessment,
            String assessmentLabel,
            String rationale,
            List<ScreeningMatchPresentation> matches) {
    }

    public record ScreeningMatchPresentation(String literal, String rationale) {
    }

    public record GeneticResourcePresentation(
            String assessment,
            String assessmentLabel,
            String rationale,
            List<String> missingInformation) {
    }

    public record HistoricalPatentabilityPresentation(String outcome, String rationale) {
    }

    public record DisclaimerPresentation(UUID id, long version, String title, String content) {
    }
}
