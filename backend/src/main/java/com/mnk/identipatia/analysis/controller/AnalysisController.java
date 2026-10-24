package com.mnk.identipatia.analysis.controller;

import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.dto.AnalysisResponse;
import com.mnk.identipatia.analysis.dto.TextAnalysisRequest;
import com.mnk.identipatia.analysis.service.AnalysisAccessResolver;
import com.mnk.identipatia.analysis.service.AnalysisApplicationService;
import com.mnk.identipatia.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/analyses")
@Tag(name = "Analyses", description = "Análisis asíncrono con acceso STANDARD por sesión o ADMIN por JWT.")
public class AnalysisController {
    private final AnalysisApplicationService service;
    private final AnalysisAccessResolver accessResolver;
    private final AnalysisProperties properties;

    public AnalysisController(AnalysisApplicationService service, AnalysisAccessResolver accessResolver,
            AnalysisProperties properties) {
        this.service = service;
        this.accessResolver = accessResolver;
        this.properties = properties;
    }

    @PostMapping("/text")
    @Operation(summary = "Crear análisis de texto",
            description = "STANDARD requiere sesión activa, consentimiento vigente y CSRF. ADMIN requiere Bearer JWT y no usa sesión ni consentimiento STANDARD. Persiste antes de responder y nunca espera al LLM.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
            })
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Análisis recibido"),
            @ApiResponse(responseCode = "401", description = "Sesión STANDARD o Bearer ADMIN inválido"),
            @ApiResponse(responseCode = "403", description = "CSRF STANDARD inválido"),
            @ApiResponse(responseCode = "409", description = "Consentimiento STANDARD vigente no aceptado"),
            @ApiResponse(responseCode = "422", description = "Descripción fuera de límites"),
            @ApiResponse(responseCode = "503", description = "IA no disponible para nuevas consultas")
    })
    public ResponseEntity<AnalysisCreatedResponse> createText(
            @RequestBody TextAnalysisRequest body, HttpServletRequest request, Authentication authentication) {
        AnalysisCreatedResponse created = service.createText(
                accessResolver.resolve(request, authentication), body == null ? null : body.description());
        long retryAfter = Math.max(1, properties.pollInterval().toSeconds());
        return ResponseEntity.accepted()
                .location(URI.create("/identipat-ia/analyses/" + created.analysisId()))
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                .body(created);
    }

    @GetMapping("/{analysisId}")
    @Operation(summary = "Consultar análisis",
            description = "STANDARD se aísla por session_id; ADMIN por user_id y contexto ADMIN. No expone invocaciones ni metadata del proveedor.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
            })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado, resultado o fallo público"),
            @ApiResponse(responseCode = "401", description = "Sesión STANDARD o Bearer ADMIN inválido"),
            @ApiResponse(responseCode = "404", description = "No existe para el actor y contexto actuales")
    })
    public AnalysisResponse get(@PathVariable UUID analysisId, HttpServletRequest request,
            Authentication authentication) {
        return service.get(accessResolver.resolve(request, authentication), analysisId);
    }
}
