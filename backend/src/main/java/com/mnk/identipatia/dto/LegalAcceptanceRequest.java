package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aceptación expresa del contenido legal mostrado por el backend.")
public record LegalAcceptanceRequest(String contentHash, Boolean accepted) {
}
