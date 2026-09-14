package com.mnk.identipatia.analysis.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.result.AnalysisResult;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID analysisId,
        AnalysisInputType inputType,
        AnalysisStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        AnalysisResult result,
        AnalysisFailureResponse failure) {
    @Override
    public String toString() {
        return "AnalysisResponse[analysisId=" + analysisId + ", inputType=" + inputType
                + ", status=" + status + ", createdAt=" + createdAt + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt + ", failedAt=" + failedAt
                + ", result=" + (result == null ? "null" : "<redacted>")
                + ", failure=" + failure + "]";
    }
}
