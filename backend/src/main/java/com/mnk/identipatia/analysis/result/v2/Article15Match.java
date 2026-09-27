package com.mnk.identipatia.analysis.result.v2;

import java.util.Objects;

public record Article15Match(Article15Literal literal, String rationale) {
    public Article15Match {
        Objects.requireNonNull(literal, "literal must not be null");
        ResultValidation.requireText(rationale, "rationale");
    }
}
