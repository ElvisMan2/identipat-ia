package com.mnk.identipatia.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Contenido legal vigente, sin PII ni metadatos internos del administrador.")
public record LegalDocumentResponse(
        UUID documentId,
        String title,
        String contentHtml,
        String contentHash,
        Instant updatedAt) {
}

