package com.mnk.identipatia.analysis.result;

import java.util.Objects;

public record PatentabilityAssessment(PatentabilityOutcome outcome, String rationale) {
    public PatentabilityAssessment {
        Objects.requireNonNull(outcome, "outcome must not be null");
        requireText(rationale, "rationale");
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
