package com.mnk.identipatia.analysis.result.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record Article20Screening(
        ScreeningAssessment assessment,
        String rationale,
        List<Article20Match> matches) {
    public Article20Screening {
        Objects.requireNonNull(assessment, "assessment must not be null");
        ResultValidation.requireText(rationale, "rationale");
        matches = ResultValidation.immutable(matches, "matches");
        validateMatches(assessment, matches);
        if (new HashSet<>(matches.stream().map(Article20Match::literal).toList()).size() != matches.size()) {
            throw new IllegalArgumentException("article20 matches must not repeat literals");
        }
    }

    private static void validateMatches(ScreeningAssessment assessment, List<Article20Match> matches) {
        if (assessment == ScreeningAssessment.POTENTIAL_MATCH && matches.isEmpty()) {
            throw new IllegalArgumentException("POTENTIAL_MATCH requires at least one article20 match");
        }
        if (assessment != ScreeningAssessment.POTENTIAL_MATCH && !matches.isEmpty()) {
            throw new IllegalArgumentException(assessment + " requires empty article20 matches");
        }
    }
}
