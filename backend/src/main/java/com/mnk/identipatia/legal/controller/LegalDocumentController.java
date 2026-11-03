package com.mnk.identipatia.legal.controller;

import com.mnk.identipatia.legal.dto.LegalDocumentResponse;
import com.mnk.identipatia.legal.service.LegalDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Legal document", description = "Único contenido legal vigente; no usa versiones funcionales.")
public class LegalDocumentController {
    private final LegalDocumentService service;

    public LegalDocumentController(LegalDocumentService service) {
        this.service = service;
    }

    @GetMapping("/legal-document")
    @Operation(summary = "Obtener contenido legal vigente",
            description = "Lectura pública sin PII. contentHash identifica el contenido saneado vigente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenido legal vigente"),
            @ApiResponse(responseCode = "404", description = "LEGAL_DOCUMENT_NOT_FOUND")
    })
    public LegalDocumentResponse get() {
        return service.getCurrent();
    }
}

