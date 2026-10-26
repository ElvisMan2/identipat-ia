package com.mnk.identipatia.reporting.controller;

import com.mnk.identipatia.analysis.service.AnalysisAccessResolver;
import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.service.AnalysisReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/analyses")
@Tag(name = "Diagnostic reports", description = "Reporte histórico derivado del resultado canónico persistido.")
public class AnalysisReportController {
    private final AnalysisReportService service;
    private final AnalysisAccessResolver accessResolver;

    public AnalysisReportController(AnalysisReportService service, AnalysisAccessResolver accessResolver) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @GetMapping(value = "/{analysisId}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener reporte diagnóstico JSON",
            description = "STANDARD requiere la sesión propietaria; ADMIN requiere Bearer y propiedad ADMIN.",
            security = {@SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte diagnóstico"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "404", description = "Análisis inexistente o ajeno"),
            @ApiResponse(responseCode = "409", description = "Reporte no listo, no disponible o versión no soportada"),
            @ApiResponse(responseCode = "500", description = "Contexto histórico no disponible")
    })
    public ResponseEntity<DiagnosticReport> get(@PathVariable UUID analysisId, HttpServletRequest request,
            Authentication authentication) {
        DiagnosticReport report = service.get(accessResolver.resolve(request, authentication), analysisId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(report);
    }

    @GetMapping(value = "/{analysisId}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descargar reporte diagnóstico PDF",
            description = "Genera el PDF en memoria desde el mismo reporte histórico usado por el endpoint JSON.",
            security = {@SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF descargable"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "404", description = "Análisis inexistente o ajeno"),
            @ApiResponse(responseCode = "409", description = "Reporte no listo, no disponible o versión no soportada"),
            @ApiResponse(responseCode = "500", description = "Contexto o generación no disponible")
    })
    public ResponseEntity<byte[]> pdf(@PathVariable UUID analysisId, HttpServletRequest request,
            Authentication authentication) {
        byte[] pdf = service.getPdf(accessResolver.resolve(request, authentication), analysisId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"identipat-diagnostico-" + analysisId + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
