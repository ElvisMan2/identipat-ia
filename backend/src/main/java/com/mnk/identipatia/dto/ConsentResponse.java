package com.mnk.identipatia.dto;

import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.StandardSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resultado de la decisión, sin PII ni IDs internos.")
public record ConsentResponse(
        String contentHash,
        ConsentDecision decision,
        Instant acceptedAt,
        StandardSessionStatus sessionStatus,
        boolean consentRequired) {
}
