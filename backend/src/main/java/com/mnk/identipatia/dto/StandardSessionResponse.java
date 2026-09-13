package com.mnk.identipatia.dto;

import com.mnk.identipatia.model.StandardSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado temporal sin PII, IDs internos ni token de sesión.")
public record StandardSessionResponse(
        StandardSessionStatus status,
        Instant expiresAt,
        Instant absoluteExpiresAt,
        boolean consentRequired,
        String requiredConsentVersion) {
}
