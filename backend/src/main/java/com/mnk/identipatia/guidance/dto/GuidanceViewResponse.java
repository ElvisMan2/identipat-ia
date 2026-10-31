package com.mnk.identipatia.guidance.dto;

import java.time.Instant;
import java.util.UUID;

public record GuidanceViewResponse(
        UUID analysisId,
        String protectionType,
        long guideVersion,
        Instant firstViewedAt) {
}

