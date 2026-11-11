package com.mnk.identipatia.reporting.controller;

import com.mnk.identipatia.reporting.dto.OperationalAnalysisExportCriteria;
import com.mnk.identipatia.reporting.service.OperationalAnalysisCsvExporter;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
public class AdminOperationalAnalysisExportController {
    private static final MediaType CSV_MEDIA_TYPE =
            MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final OperationalAnalysisDatasetService datasetService;
    private final OperationalAnalysisCsvExporter csvExporter;

    public AdminOperationalAnalysisExportController(
            OperationalAnalysisDatasetService datasetService,
            OperationalAnalysisCsvExporter csvExporter) {
        this.datasetService = datasetService;
        this.csvExporter = csvExporter;
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    @Operation(
            summary = "Exportar reporte operacional de análisis",
            description = "Exporta el dataset operacional de análisis en CSV UTF-8 con BOM, "
                    + "con los mismos filtros y orden lógico de la tabla administrativa, sin paginación.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "CSV operacional de 20 columnas",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                    description = "attachment; filename=identipat-analisis_....csv"),
                            @Header(name = HttpHeaders.CACHE_CONTROL,
                                    description = "no-store, private")
                    },
                    content = @Content(mediaType = "text/csv",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Parámetros de consulta inválidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN"),
            @ApiResponse(responseCode = "500", description = "Inconsistencia interna del dataset")
    })
    public ResponseEntity<byte[]> export(
            @Parameter(description = "Términos de usuario combinados con AND", example = "jose zuniga")
            @RequestParam(required = false) String search,
            @Parameter(description = "Fecha inicial inclusiva en formato YYYY-MM-DD", example = "2026-10-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha final inclusiva en formato YYYY-MM-DD", example = "2026-10-31")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "RECEIVED, PREPROCESSING, ANALYZING, COMPLETED o FAILED")
            @RequestParam(required = false) String status,
            @Parameter(description = "STANDARD o ADMIN")
            @RequestParam(required = false) String origin,
            @Parameter(description = "TEXT, PDF o AUDIO")
            @RequestParam(required = false) String inputType,
            @Parameter(description = "field,direction; campos: createdAt, status, inputType, analysisId",
                    example = OperationalAnalysisExportCriteria.DEFAULT_SORT)
            @RequestParam(required = false) String sort) {
        OperationalAnalysisExportCriteria criteria = OperationalAnalysisExportCriteria.of(
                search, from, to, status, origin, inputType, sort);
        byte[] csv = csvExporter.export(datasetService.build(criteria));
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename(criteria)).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(csv);
    }

    private static String filename(OperationalAnalysisExportCriteria criteria) {
        LocalDate from = criteria.from();
        LocalDate to = criteria.to();
        String suffix = criteria.hasAdditionalFilters() ? "_filtrado" : "";
        if (from != null && to != null) {
            return "identipat-analisis_" + from + "_" + to + suffix + ".csv";
        }
        if (from != null) {
            return "identipat-analisis_desde_" + from + suffix + ".csv";
        }
        if (to != null) {
            return "identipat-analisis_hasta_" + to + suffix + ".csv";
        }
        return criteria.hasAdditionalFilters()
                ? "identipat-analisis_filtrado.csv"
                : "identipat-analisis_completo.csv";
    }
}
