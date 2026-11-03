package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Fila liviana de la tabla administrativa de analisis.")
public record OperationalAnalysisListItem(
        UUID analysisId,
        Instant createdAt,
        AnalysisStatus status,
        String firstName,
        String lastNames,
        String doiType,
        String doi,
        AnalysisInputType inputType,
        String conclusion,
        List<String> identifiedModalities,
        OperationalAnalysisOrigin analysisOrigin,
        boolean guidanceConsulted) {

    public OperationalAnalysisListItem {
        identifiedModalities = List.copyOf(identifiedModalities);
    }

    @Override
    public String toString() {
        return "OperationalAnalysisListItem[analysisId=" + analysisId
                + ", createdAt=" + createdAt
                + ", status=" + status
                + ", inputType=" + inputType
                + ", analysisOrigin=" + analysisOrigin
                + ", guidanceConsulted=" + guidanceConsulted
                + ", content=<redacted>]";
    }
}
