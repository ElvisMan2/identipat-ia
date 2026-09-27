package com.mnk.identipatia.analysis.result.v2;

import java.util.Objects;

public record Article20Match(Article20Literal literal, String rationale) {
    public Article20Match {
        Objects.requireNonNull(literal, "literal must not be null");
        ResultValidation.requireText(rationale, "rationale");
    }
}
