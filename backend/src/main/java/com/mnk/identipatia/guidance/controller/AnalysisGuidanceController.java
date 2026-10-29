package com.mnk.identipatia.guidance.controller;

import com.mnk.identipatia.analysis.service.AnalysisAccessResolver;
import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.guidance.dto.AnalysisGuidanceResponse;
import com.mnk.identipatia.guidance.service.AnalysisGuidanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
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
@Tag(name = "Analysis guidance", description = "Orientación institucional ACTIVE vigente por modalidad.")
public class AnalysisGuidanceController {
    private final AnalysisGuidanceService service;
    private final AnalysisAccessResolver accessResolver;

    public AnalysisGuidanceController(AnalysisGuidanceService service, AnalysisAccessResolver accessResolver) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @GetMapping(value = "/{analysisId}/guidance", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener orientación institucional vigente",
            description = "STANDARD requiere la sesión propietaria; ADMIN requiere Bearer y propiedad ADMIN. "
                    + "contentHtml cumple la política protection-guide-html/1.0.",
            security = {@SecurityRequirement(name = OpenApiConfig.STANDARD_SESSION),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Guidance resuelta con el catálogo ACTIVE actual"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "404", description = "Análisis inexistente o ajeno"),
            @ApiResponse(responseCode = "409", description = "Guidance no lista, no disponible o no soportada"),
            @ApiResponse(responseCode = "500", description = "GUIDANCE_CATALOG_INCOMPLETE")
    })
    public ResponseEntity<AnalysisGuidanceResponse> get(@PathVariable UUID analysisId,
            HttpServletRequest request, Authentication authentication) {
        AnalysisGuidanceResponse response = service.get(accessResolver.resolve(request, authentication), analysisId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
