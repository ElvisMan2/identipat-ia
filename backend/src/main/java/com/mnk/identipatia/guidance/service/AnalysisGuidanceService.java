package com.mnk.identipatia.guidance.service;

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

    private final GuidanceAnalysisResolver analysisResolver;
    private final ProtectionGuideRepository guideRepository;
    private final Clock clock;

    public AnalysisGuidanceService(GuidanceAnalysisResolver analysisResolver,
            ProtectionGuideRepository guideRepository,
            Clock clock) {
        this.analysisResolver = analysisResolver;
        this.guideRepository = guideRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalysisGuidanceResponse get(AnalysisAccessContext access, UUID analysisId) {
        List<GuidanceAnalysisResolver.Option> options = analysisResolver.resolve(access, analysisId).options();
        if (options.isEmpty()) {
            return new AnalysisGuidanceResponse(GUIDANCE_VERSION, analysisId, clock.instant(), List.of());
        }

        List<ProtectionType> types = options.stream().map(GuidanceAnalysisResolver.Option::type).distinct().toList();
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

    private AnalysisGuidanceResponse.Modality modality(GuidanceAnalysisResolver.Option option, ProtectionGuide guide) {
        AnalysisGuidanceResponse.NextSteps nextSteps = "UNLIKELY".equals(option.applicability())
                ? null : new AnalysisGuidanceResponse.NextSteps(guide.getNextStepsContentHtml());
        return new AnalysisGuidanceResponse.Modality(option.type().name(), protectionLabel(option.type()),
                option.applicability(), applicabilityLabel(option.applicability()), guide.getVersion(),
                guide.getShortDescription(), nextSteps, guide.getLastVerifiedAt());
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

}
