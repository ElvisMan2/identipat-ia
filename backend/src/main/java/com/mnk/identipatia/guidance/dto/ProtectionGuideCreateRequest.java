package com.mnk.identipatia.guidance.dto;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;

public record ProtectionGuideCreateRequest(
        ProtectionType protectionType,
        String shortDescription,
        String nextStepsContentHtml) {
    @Override
    public String toString() {
        return "ProtectionGuideCreateRequest[protectionType=" + protectionType + ", content=<redacted>]";
    }
}
