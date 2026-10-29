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
import com.mnk.identipatia.guidance.dto.AnalysisGuidanceResponse;
import com.mnk.identipatia.guidance.model.ProtectionGuide;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;
import com.mnk.identipatia.guidance.repository.ProtectionGuideRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisGuidanceService {
    public static final String GUIDANCE_VERSION = "analysis-guidance/1.0";

    private final AnalysisRepository analysisRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final ProtectionGuideRepository guideRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnalysisGuidanceService(AnalysisRepository analysisRepository,
            StoredAnalysisResultRepository resultRepository,
            ProtectionGuideRepository guideRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.guideRepository = guideRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalysisGuidanceResponse get(AnalysisAccessContext access, UUID analysisId) {
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
                .orElseThrow(AnalysisGuidanceService::unsupportedVersion);
        List<Option> options = options(read(stored));
        if (options.isEmpty()) {
            return new AnalysisGuidanceResponse(GUIDANCE_VERSION, analysisId, clock.instant(), List.of());
        }

        List<ProtectionType> types = options.stream().map(Option::type).distinct().toList();
        Map<ProtectionType, ProtectionGuide> activeByType = new LinkedHashMap<>();
        guideRepository.findAllByProtectionTypeInAndStatus(types, ProtectionGuideStatus.ACTIVE)
                .forEach(guide -> activeByType.put(guide.getProtectionType(), guide));
        if (activeByType.size() != types.size()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GUIDANCE_CATALOG_INCOMPLETE",
                    "The active guidance catalog is incomplete");
        }

        List<AnalysisGuidanceResponse.Modality> modalities = options.stream()
                .map(option -> modality(option, activeByType.get(option.type())))
                .toList();
        return new AnalysisGuidanceResponse(GUIDANCE_VERSION, analysisId, clock.instant(), modalities);
    }

    private AnalysisGuidanceResponse.Modality modality(Option option, ProtectionGuide guide) {
        AnalysisGuidanceResponse.NextSteps nextSteps = "UNLIKELY".equals(option.applicability())
                ? null : new AnalysisGuidanceResponse.NextSteps(guide.getNextStepsContentHtml());
        return new AnalysisGuidanceResponse.Modality(option.type().name(), protectionLabel(option.type()),
                option.applicability(), applicabilityLabel(option.applicability()), guide.getVersion(),
                guide.getShortDescription(), nextSteps, guide.getLastVerifiedAt());
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

    private static String protectionLabel(ProtectionType type) {
        return switch (type) {
            case INVENTION_PATENT -> "Patente de invención";
            case UTILITY_MODEL -> "Modelo de utilidad";
            case INDUSTRIAL_DESIGN -> "Diseño industrial";
            case COPYRIGHT -> "Derecho de autor";
            case DISTINCTIVE_SIGN -> "Signo distintivo";
            case PLANT_BREEDER_CERTIFICATE -> "Certificado de obtentor";
            case TRADE_SECRET -> "Secreto empresarial";
        };
    }

    private static String applicabilityLabel(String applicability) {
        return switch (applicability) {
            case "LIKELY" -> "Se relaciona claramente";
            case "POSSIBLE" -> "Podría corresponder";
            case "UNLIKELY" -> "La relación es limitada";
            default -> throw unsupportedVersion();
        };
    }

    private static ApiException unsupportedVersion() {
        return new ApiException(HttpStatus.CONFLICT, "GUIDANCE_UNSUPPORTED_RESULT_VERSION",
                "The stored analysis result version is not supported for guidance");
    }

    private record Option(ProtectionType type, String applicability) {
    }
}
