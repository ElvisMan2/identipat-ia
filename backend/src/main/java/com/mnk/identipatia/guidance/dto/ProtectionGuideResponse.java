package com.mnk.identipatia.guidance.dto;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.ProtectionGuideSource;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;

import java.time.Instant;
import java.util.UUID;

public record ProtectionGuideResponse(
        UUID guideId,
        ProtectionType protectionType,
        long version,
        ProtectionGuideStatus status,
        String shortDescription,
        String nextStepsContentHtml,
        String contentSha256,
        ProtectionGuideSource createdSource,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant retiredAt,
        Instant lastVerifiedAt) {
    @Override
    public String toString() {
        return "ProtectionGuideResponse[guideId=" + guideId + ", protectionType=" + protectionType
                + ", version=" + version + ", status=" + status + ", content=<redacted>]";
    }
}
