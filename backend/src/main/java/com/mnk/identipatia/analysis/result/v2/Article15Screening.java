package com.mnk.identipatia.analysis.result.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record Article15Screening(
        ScreeningAssessment assessment,
        String rationale,
        List<Article15Match> matches) {
    public Article15Screening {
        Objects.requireNonNull(assessment, "assessment must not be null");
        ResultValidation.requireText(rationale, "rationale");
        matches = ResultValidation.immutable(matches, "matches");
        validateMatches(assessment, matches);
        if (new HashSet<>(matches.stream().map(Article15Match::literal).toList()).size() != matches.size()) {
            throw new IllegalArgumentException("article15 matches must not repeat literals");
        }
    }

    private static void validateMatches(ScreeningAssessment assessment, List<Article15Match> matches) {
        if (assessment == ScreeningAssessment.POTENTIAL_MATCH && matches.isEmpty()) {
            throw new IllegalArgumentException("POTENTIAL_MATCH requires at least one article15 match");
        }
        if (assessment != ScreeningAssessment.POTENTIAL_MATCH && !matches.isEmpty()) {
            throw new IllegalArgumentException(assessment + " requires empty article15 matches");
        }
    }
}
