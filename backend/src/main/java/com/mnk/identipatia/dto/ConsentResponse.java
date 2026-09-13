package com.mnk.identipatia.dto;

import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.StandardSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Evidencia funcional de la decisión, sin PII, IDs internos ni hash documental.")
public record ConsentResponse(
        String consentVersion,
        ConsentDecision decision,
        Instant decidedAt,
        StandardSessionStatus sessionStatus,
        boolean consentRequired) {
}
