package com.mnk.identipatia.reporting.controller;

import com.mnk.identipatia.dto.PagedResponse;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListItem;
import com.mnk.identipatia.reporting.service.OperationalAnalysisQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/admin/analyses")
@Tag(name = "Reporting", description = "Reportes administrativos operacionales.")
public class AdminOperationalAnalysisController {
    private final OperationalAnalysisQueryService queryService;

    public AdminOperationalAnalysisController(OperationalAnalysisQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Listar analisis operacionales",
            description = "Consulta administrativa paginada con busqueda por perfil actual, filtros y orden estable.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de analisis operacionales"),
            @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o invalido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "500", description = "Inconsistencia interna del dataset")
    })
    public ResponseEntity<PagedResponse<OperationalAnalysisListItem>> findAll(
            @Parameter(description = "Pagina basada en cero", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elementos por pagina, entre 1 y 100", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Terminos de usuario combinados con AND", example = "jose zuniga")
            @RequestParam(required = false) String search,
            @Parameter(description = "Fecha inicial inclusiva YYYY-MM-DD", example = "2026-10-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha final inclusiva YYYY-MM-DD", example = "2026-10-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "RECEIVED, PREPROCESSING, ANALYZING, COMPLETED o FAILED")
            @RequestParam(required = false) String status,
            @Parameter(description = "STANDARD o ADMIN")
            @RequestParam(required = false) String origin,
            @Parameter(description = "TEXT, PDF o AUDIO")
            @RequestParam(required = false) String inputType,
            @Parameter(description = "field,direction; campos: createdAt, status, inputType, analysisId",
                    example = OperationalAnalysisListCriteria.DEFAULT_SORT)
            @RequestParam(defaultValue = OperationalAnalysisListCriteria.DEFAULT_SORT) String sort) {
        OperationalAnalysisListCriteria criteria = OperationalAnalysisListCriteria.of(
                page, size, search, from, to, status, origin, inputType, sort);
        return ResponseEntity.ok(queryService.findAll(criteria));
    }
}
