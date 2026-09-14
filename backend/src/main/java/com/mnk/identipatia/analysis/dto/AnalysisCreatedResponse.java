package com.mnk.identipatia.analysis.dto;

import com.mnk.identipatia.analysis.model.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalysisCreatedResponse(UUID analysisId, AnalysisStatus status, Instant createdAt) {
}
