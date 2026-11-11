package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OperationalAnalysisReportRow(
        UUID analysisId,
        String conclusion,
        AnalysisStatus analysisStatus,
        String reportText,
        boolean reportPdfAvailable,
        String firstName,
        String lastNames,
        String doiType,
        String doi,
        String gender,
        LocalDate birthDate,
        String mobilePhone,
        String email,
        String profession,
        Instant createdAt,
        AnalysisInputType inputType,
        List<String> identifiedModalities,
        List<OperationalModalityApplicability> applicabilityByModality,
        OperationalAnalysisOrigin analysisOrigin,
        boolean guidanceConsulted) {

    public OperationalAnalysisReportRow {
        identifiedModalities = List.copyOf(identifiedModalities);
        applicabilityByModality = List.copyOf(applicabilityByModality);
    }

    @Override
    public String toString() {
        return "OperationalAnalysisReportRow[analysisId=" + analysisId
                + ", analysisStatus=" + analysisStatus
                + ", createdAt=" + createdAt
                + ", inputType=" + inputType
                + ", analysisOrigin=" + analysisOrigin
                + ", guidanceConsulted=" + guidanceConsulted
                + ", content=<redacted>]";
    }
}
