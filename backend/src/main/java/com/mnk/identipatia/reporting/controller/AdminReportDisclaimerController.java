package com.mnk.identipatia.reporting.controller;

import com.mnk.identipatia.config.OpenApiConfig;
import com.mnk.identipatia.reporting.dto.DisclaimerUpsertRequest;
import com.mnk.identipatia.reporting.dto.ReportDisclaimerResponse;
import com.mnk.identipatia.reporting.service.ReportDisclaimerService;
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
@RequestMapping("/admin/report-disclaimers")
@Tag(name = "Report disclaimers", description = "Gobierno ADMIN del disclaimer institucional versionado.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación completada"),
        @ApiResponse(responseCode = "201", description = "DRAFT creado"),
        @ApiResponse(responseCode = "401", description = "Bearer ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "El actor autenticado no es ADMIN"),
        @ApiResponse(responseCode = "404", description = "DISCLAIMER_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "Disclaimer inmutable o ya activo"),
        @ApiResponse(responseCode = "422", description = "INVALID_DISCLAIMER")
})
public class AdminReportDisclaimerController {
    private final ReportDisclaimerService service;

    public AdminReportDisclaimerController(ReportDisclaimerService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar versiones del disclaimer")
    public List<ReportDisclaimerResponse> list() {
        return service.list();
    }

    @GetMapping("/active")
    @Operation(summary = "Obtener el disclaimer activo")
    public ReportDisclaimerResponse active() {
        return service.active();
    }

    @GetMapping("/{disclaimerId}")
    @Operation(summary = "Obtener una versión del disclaimer")
    public ReportDisclaimerResponse get(@PathVariable UUID disclaimerId) {
        return service.get(disclaimerId);
    }

    @PostMapping
    @Operation(summary = "Crear un disclaimer DRAFT")
    public ResponseEntity<ReportDisclaimerResponse> create(@RequestBody(required = false) DisclaimerUpsertRequest body,
            Authentication authentication) {
        ReportDisclaimerResponse created = service.create(body, authentication);
        return ResponseEntity.created(URI.create("/identipat-ia/admin/report-disclaimers/" + created.id()))
                .body(created);
    }

    @PostMapping("/active/draft")
    @Operation(summary = "Clonar el disclaimer activo como DRAFT")
    public ResponseEntity<ReportDisclaimerResponse> cloneActive(Authentication authentication) {
        ReportDisclaimerResponse created = service.cloneActive(authentication);
        return ResponseEntity.created(URI.create("/identipat-ia/admin/report-disclaimers/" + created.id()))
                .body(created);
    }

    @PutMapping("/{disclaimerId}")
    @Operation(summary = "Editar un disclaimer DRAFT")
    public ReportDisclaimerResponse update(@PathVariable UUID disclaimerId,
            @RequestBody(required = false) DisclaimerUpsertRequest body, Authentication authentication) {
        return service.update(disclaimerId, body, authentication);
    }

    @PostMapping("/{disclaimerId}/activate")
    @Operation(summary = "Activar un disclaimer DRAFT y retirar el activo anterior")
    public ReportDisclaimerResponse activate(@PathVariable UUID disclaimerId, Authentication authentication) {
        return service.activate(disclaimerId, authentication);
    }
}
