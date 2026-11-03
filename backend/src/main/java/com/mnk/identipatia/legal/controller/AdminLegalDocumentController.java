package com.mnk.identipatia.legal.controller;

import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.legal.dto.LegalDocumentResponse;
import com.mnk.identipatia.legal.dto.UpdateLegalDocumentRequest;
import com.mnk.identipatia.legal.service.LegalDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/legal-document")
@Tag(name = "Legal document administration",
        description = "Edición directa del único contenido vigente, sin drafts, publicación ni versiones.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminLegalDocumentController {
    private final LegalDocumentService service;

    public AdminLegalDocumentController(LegalDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Obtener contenido legal vigente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenido legal vigente"),
            @ApiResponse(responseCode = "401", description = "Bearer ADMIN ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El actor no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "LEGAL_DOCUMENT_NOT_FOUND")
    })
    public LegalDocumentResponse get() {
        return service.getCurrent();
    }

    @PutMapping
    @Operation(summary = "Configurar o reemplazar el contenido legal vigente",
            description = "Sanitiza con legal-document-html/1.0 y calcula SHA-256 server-side.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenido vigente guardado"),
            @ApiResponse(responseCode = "400", description = "INVALID_LEGAL_DOCUMENT"),
            @ApiResponse(responseCode = "401", description = "Bearer ADMIN ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El actor no es ADMIN")
    })
    public LegalDocumentResponse update(@RequestBody(required = false) UpdateLegalDocumentRequest body,
            Authentication authentication) {
        return service.update(body, authentication);
    }
}

