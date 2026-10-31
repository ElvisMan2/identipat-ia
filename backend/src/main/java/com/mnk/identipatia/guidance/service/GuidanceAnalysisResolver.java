package com.mnk.identipatia.guidance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.analysis.service.AnalysisAccessContext;
import com.mnk.identipatia.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GuidanceAnalysisResolver {
    private final AnalysisRepository analysisRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    public GuidanceAnalysisResolver(AnalysisRepository analysisRepository,
            StoredAnalysisResultRepository resultRepository, ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    public ResolvedAnalysis resolve(AnalysisAccessContext access, UUID analysisId) {
        Analysis analysis = findOwned(access, analysisId);
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "GUIDANCE_UNAVAILABLE",
                    "Guidance is unavailable for a failed analysis");
        }
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "GUIDANCE_NOT_READY",
                    "The analysis is not completed yet");
        }

        StoredAnalysisResult stored = resultRepository.findById(analysisId)
                .orElseThrow(GuidanceAnalysisResolver::unsupportedVersion);
        return new ResolvedAnalysis(analysis, options(read(stored)));
    }

    public Option requireOption(ResolvedAnalysis resolved, ProtectionType protectionType) {
        return resolved.options().stream()
                .filter(option -> option.type() == protectionType)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDANCE_MODALITY_NOT_FOUND",
                        "The protection modality does not belong to the analysis"));
    }

    private Analysis findOwned(AnalysisAccessContext access, UUID analysisId) {
        return (access.mode() == AnalysisAccessContext.Mode.ADMIN_JWT
                ? analysisRepository.findByAnalysisIdAndUserIdAndSessionIdIsNull(
                        analysisId, access.user().getUserId())
                : analysisRepository.findByAnalysisIdAndSessionId(analysisId, access.session().getSessionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                        "Analysis was not found"));
    }

    private AnalysisResult read(StoredAnalysisResult stored) {
        try {
            return new AnalysisResultReader(objectMapper).read(stored.getSchemaVersion(), stored.getResultJson());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw unsupportedVersion();
        }
    }

    private List<Option> options(AnalysisResult result) {
        if (result instanceof AnalysisResultV2 v2) {
            return v2.protectionOptions().stream()
                    .map(option -> new Option(option.type(), option.applicability().name()))
                    .toList();
        }
        if (result instanceof AnalysisResultV1 v1) {
            try {
                return v1.protectionOptions().stream()
                        .map(option -> new Option(ProtectionType.valueOf(option.type().name()),
                                option.applicability().name()))
                        .toList();
            } catch (IllegalArgumentException exception) {
                throw unsupportedVersion();
            }
        }
        throw unsupportedVersion();
    }

    private static ApiException unsupportedVersion() {
        return new ApiException(HttpStatus.CONFLICT, "GUIDANCE_UNSUPPORTED_RESULT_VERSION",
                "The stored analysis result version is not supported for guidance");
    }

    public record ResolvedAnalysis(Analysis analysis, List<Option> options) {
    }

    public record Option(ProtectionType type, String applicability) {
    }
}

