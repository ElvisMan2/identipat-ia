package com.mnk.identipatia.analysis.result.v2;

import java.util.List;
import java.util.Objects;

public record GeneticResourceAccess(
        GeneticResourceAssessment assessment,
        String rationale,
        List<String> missingInformation) {
    public GeneticResourceAccess {
        Objects.requireNonNull(assessment, "assessment must not be null");
        ResultValidation.requireText(rationale, "rationale");
        missingInformation = ResultValidation.immutableText(missingInformation, "missingInformation");
        if (assessment == GeneticResourceAssessment.INSUFFICIENT_INFORMATION && missingInformation.isEmpty()) {
            throw new IllegalArgumentException("INSUFFICIENT_INFORMATION requires missingInformation");
        }
    }
}
