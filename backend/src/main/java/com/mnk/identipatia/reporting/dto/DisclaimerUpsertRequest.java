package com.mnk.identipatia.reporting.dto;

public record DisclaimerUpsertRequest(String title, String content) {

    @Override
    public String toString() {
        return "DisclaimerUpsertRequest[title=<redacted>, content=<redacted>]";
    }
}
