package com.mnk.identipatia.guidance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisGuidanceResponse(
        String guidanceVersion,
        UUID analysisId,
        Instant resolvedAt,
        List<Modality> modalities) {

    @Override
    public String toString() {
        return "AnalysisGuidanceResponse[guidanceVersion=" + guidanceVersion + ", analysisId=" + analysisId
                + ", resolvedAt=" + resolvedAt + ", content=<redacted>]";
    }

    public record Modality(
            String protectionType,
            String name,
            String applicability,
            String applicabilityLabel,
            long guideVersion,
            String shortDescription,
            NextSteps nextSteps,
            Instant lastVerifiedAt) {
        @Override
        public String toString() {
            return "Modality[protectionType=" + protectionType + ", guideVersion=" + guideVersion
                    + ", content=<redacted>]";
        }
    }

    public record NextSteps(String contentHtml) {
        @Override
        public String toString() {
            return "NextSteps[content=<redacted>]";
        }
    }
}
