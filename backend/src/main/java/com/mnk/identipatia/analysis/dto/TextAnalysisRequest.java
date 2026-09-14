package com.mnk.identipatia.analysis.dto;

public record TextAnalysisRequest(String description) {
    @Override
    public String toString() {
        return "TextAnalysisRequest[description=<redacted>]";
    }
}
