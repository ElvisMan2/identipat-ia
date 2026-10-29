package com.mnk.identipatia.guidance.controller;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.guidance.dto.ProtectionGuideCreateRequest;
import com.mnk.identipatia.guidance.dto.ProtectionGuideResponse;
import com.mnk.identipatia.guidance.dto.ProtectionGuideUpdateRequest;
import com.mnk.identipatia.guidance.service.ProtectionGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/protection-guides")
@Tag(name = "Protection guides", description = "Catálogo institucional versionado con HTML sanitizado.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación completada"),
        @ApiResponse(responseCode = "201", description = "DRAFT creado"),
        @ApiResponse(responseCode = "401", description = "Bearer ADMIN ausente o inválido"),
        @ApiResponse(responseCode = "404", description = "Guía inexistente o ACTIVE no encontrada"),
        @ApiResponse(responseCode = "409", description = "Guía inmutable o ya activa"),
        @ApiResponse(responseCode = "422", description = "INVALID_PROTECTION_GUIDE")
})
public class AdminProtectionGuideController {
    private final ProtectionGuideService service;

    public AdminProtectionGuideController(ProtectionGuideService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar versiones de las guías")
    public List<ProtectionGuideResponse> list() {
        return service.list();
    }

    @GetMapping("/active/{protectionType}")
    @Operation(summary = "Obtener la guía ACTIVE de una modalidad")
    public ProtectionGuideResponse active(@PathVariable ProtectionType protectionType) {
        return service.active(protectionType);
    }

    @GetMapping("/{guideId}")
    @Operation(summary = "Obtener una versión de guía")
    public ProtectionGuideResponse get(@PathVariable UUID guideId) {
        return service.get(guideId);
    }

    @PostMapping
    @Operation(summary = "Crear una guía DRAFT",
            description = "El contenido enriquecido se sanitiza con protection-guide-html/1.0.")
    public ResponseEntity<ProtectionGuideResponse> create(
            @RequestBody(required = false) ProtectionGuideCreateRequest body,
            Authentication authentication) {
        ProtectionGuideResponse created = service.create(body, authentication);
        return created(created);
    }

    @PutMapping("/{guideId}")
    @Operation(summary = "Editar una guía DRAFT")
    public ProtectionGuideResponse update(@PathVariable UUID guideId,
            @RequestBody(required = false) ProtectionGuideUpdateRequest body,
            Authentication authentication) {
        return service.update(guideId, body, authentication);
    }

    @PostMapping("/active/{protectionType}/draft")
    @Operation(summary = "Clonar la guía ACTIVE como DRAFT")
    public ResponseEntity<ProtectionGuideResponse> cloneActive(@PathVariable ProtectionType protectionType,
            Authentication authentication) {
        return created(service.cloneActive(protectionType, authentication));
    }

    @PostMapping("/{guideId}/activate")
    @Operation(summary = "Activar una guía DRAFT y retirar la ACTIVE anterior")
    public ProtectionGuideResponse activate(@PathVariable UUID guideId, Authentication authentication) {
        return service.activate(guideId, authentication);
    }

    private static ResponseEntity<ProtectionGuideResponse> created(ProtectionGuideResponse response) {
        return ResponseEntity.created(URI.create("/identipat-ia/admin/protection-guides/" + response.guideId()))
                .body(response);
    }
}
