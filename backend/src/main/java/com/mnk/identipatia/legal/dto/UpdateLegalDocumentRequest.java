package com.mnk.identipatia.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contenido legal vigente. El backend sanitiza el HTML y calcula el SHA-256.")
public record UpdateLegalDocumentRequest(String title, String contentHtml) {
}

