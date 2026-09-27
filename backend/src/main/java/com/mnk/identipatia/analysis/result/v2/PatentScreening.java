package com.mnk.identipatia.analysis.result.v2;

import java.util.Objects;

public record PatentScreening(
        boolean applicable,
        Article15Screening article15,
        Article20Screening article20) {
    public PatentScreening {
        Objects.requireNonNull(article15, "article15 must not be null");
        Objects.requireNonNull(article20, "article20 must not be null");
        if (!applicable && (article15.assessment() != ScreeningAssessment.NO_POTENTIAL_MATCH
                || article20.assessment() != ScreeningAssessment.NO_POTENTIAL_MATCH)) {
            throw new IllegalArgumentException("Non-applicable patent screening must have no potential matches");
        }
    }
}
