package com.mnk.identipatia.analysis.result.v3;

import java.util.List;
import java.util.Objects;

public record TraditionalKnowledgeAccess(
        TraditionalKnowledgeAssessment assessment,
        String rationale,
        List<String> missingInformation) {

    public TraditionalKnowledgeAccess {
        Objects.requireNonNull(assessment, "assessment must not be null");
        requireText(rationale, "rationale");
        Objects.requireNonNull(missingInformation, "missingInformation must not be null");
        if (missingInformation.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("missingInformation must not contain null values");
        }
        missingInformation.forEach(value -> requireText(value, "missingInformation item"));
        missingInformation = List.copyOf(missingInformation);
        if (assessment == TraditionalKnowledgeAssessment.INSUFFICIENT_INFORMATION
                && missingInformation.isEmpty()) {
            throw new IllegalArgumentException("INSUFFICIENT_INFORMATION requires missingInformation");
        }
        if (assessment == TraditionalKnowledgeAssessment.NOT_INDICATED
                && !missingInformation.isEmpty()) {
            throw new IllegalArgumentException("NOT_INDICATED requires empty missingInformation");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
