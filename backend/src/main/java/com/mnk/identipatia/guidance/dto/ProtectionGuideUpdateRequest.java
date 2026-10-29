package com.mnk.identipatia.guidance.dto;

public record ProtectionGuideUpdateRequest(String shortDescription, String nextStepsContentHtml) {
    @Override
    public String toString() {
        return "ProtectionGuideUpdateRequest[content=<redacted>]";
    }
}
