package com.mnk.identipatia.analysis.controller;

import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.dto.AnalysisResponse;
import com.mnk.identipatia.analysis.dto.TextAnalysisRequest;
import com.mnk.identipatia.analysis.service.AnalysisApplicationService;
import com.mnk.identipatia.service.StandardSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "STANDARD analyses", description = "Análisis asíncrono accesible solo desde la sesión STANDARD que lo creó.")
public class AnalysisController {
    private final AnalysisApplicationService service;
    private final StandardSessionCookieService cookieService;
    private final AnalysisProperties properties;

    public AnalysisController(AnalysisApplicationService service, StandardSessionCookieService cookieService,
            AnalysisProperties properties) {
        this.service = service;
        this.cookieService = cookieService;
        this.properties = properties;
    }

    @PostMapping("/text")
    @Operation(summary = "Crear análisis de texto", description = "Requiere sesión STANDARD activa, consentimiento vigente y CSRF. Persiste antes de responder y nunca espera al LLM.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Análisis recibido"),
            @ApiResponse(responseCode = "401", description = "Sesión STANDARD inválida"),
            @ApiResponse(responseCode = "403", description = "CSRF inválido"),
            @ApiResponse(responseCode = "409", description = "Consentimiento vigente no aceptado"),
            @ApiResponse(responseCode = "422", description = "Descripción fuera de límites"),
            @ApiResponse(responseCode = "503", description = "IA no disponible para nuevas consultas")
    })
    public ResponseEntity<AnalysisCreatedResponse> createText(
            @RequestBody TextAnalysisRequest body, HttpServletRequest request) {
        AnalysisCreatedResponse created = service.createText(
                cookieService.readToken(request), body == null ? null : body.description());
        long retryAfter = Math.max(1, properties.pollInterval().toSeconds());
        return ResponseEntity.accepted()
                .location(URI.create("/identipat-ia/analyses/" + created.analysisId()))
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                .body(created);
    }

    @GetMapping("/{analysisId}")
    @Operation(summary = "Consultar análisis", description = "Exige sesión STANDARD activa y pertenencia a la misma session_id. No expone invocaciones ni metadata del proveedor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado, resultado o fallo público"),
            @ApiResponse(responseCode = "401", description = "Sesión STANDARD inválida"),
            @ApiResponse(responseCode = "404", description = "No existe en la sesión actual")
    })
    public AnalysisResponse get(@PathVariable UUID analysisId, HttpServletRequest request) {
        return service.get(cookieService.readToken(request), analysisId);
    }
}
