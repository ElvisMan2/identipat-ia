package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.reporting.model.ReportDisclaimerSource;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;

import java.time.Instant;
import java.util.UUID;

public record ReportDisclaimerResponse(
        UUID id,
        long version,
        String title,
        String content,
        ReportDisclaimerStatus status,
        ReportDisclaimerSource createdSource,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant retiredAt) {

    @Override
    public String toString() {
        return "ReportDisclaimerResponse[id=" + id
                + ", version=" + version
                + ", status=" + status
                + ", createdSource=" + createdSource
                + ", title=<redacted>, content=<redacted>]";
    }
}
