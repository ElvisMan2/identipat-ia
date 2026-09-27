package com.mnk.identipatia.analysis.result.v2;

import java.util.List;
import java.util.Objects;

public record LegalBasis(LegalInstrument instrument, List<String> articles) {
    public LegalBasis {
        Objects.requireNonNull(instrument, "instrument must not be null");
        articles = ResultValidation.immutableText(articles, "articles");
        if (articles.isEmpty()) {
            throw new IllegalArgumentException("articles must not be empty");
        }
    }
}
