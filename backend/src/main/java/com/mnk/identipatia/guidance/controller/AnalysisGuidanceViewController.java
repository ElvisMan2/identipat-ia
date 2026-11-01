package com.mnk.identipatia.guidance.controller;

import com.mnk.identipatia.analysis.service.AnalysisAccessResolver;
import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.guidance.dto.GuidanceViewRequest;
import com.mnk.identipatia.guidance.dto.GuidanceViewResponse;
import com.mnk.identipatia.guidance.service.GuidanceViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/analyses")
@Tag(name = "Analysis guidance", description = "Orientación institucional y registro de apertura.")
public class AnalysisGuidanceViewController {
    private final GuidanceViewService service;
    private final AnalysisAccessResolver accessResolver;

    public AnalysisGuidanceViewController(GuidanceViewService service, AnalysisAccessResolver accessResolver) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @PostMapping(value = "/{analysisId}/guidance/{protectionType}/view",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar primera apertura de próximos pasos",
            description = "Registra idempotentemente la primera apertura para una modalidad y versión concretas. "
                    + "Requiere sesión STANDARD propietaria y CSRF.",
            security = @SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visualización registrada o ya existente"),
            @ApiResponse(responseCode = "400", description = "Modalidad o request inválido"),
            @ApiResponse(responseCode = "401", description = "Sesión o Bearer inválido"),
            @ApiResponse(responseCode = "403", description = "CSRF inválido o actor ADMIN"),
            @ApiResponse(responseCode = "404", description = "Análisis ajeno o modalidad ausente"),
            @ApiResponse(responseCode = "409", description = "Análisis, resultado, applicability o versión no disponible")
    })
    public ResponseEntity<GuidanceViewResponse> record(
            @PathVariable UUID analysisId,
            @PathVariable String protectionType,
            @Valid @RequestBody GuidanceViewRequest body,
            BindingResult bindingResult,
            HttpServletRequest request,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            throw GuidanceViewService.invalidRequest();
        }
        return ResponseEntity.ok(service.record(accessResolver.resolve(request, authentication),
                analysisId, protectionType, body.guideVersion()));
    }
}

