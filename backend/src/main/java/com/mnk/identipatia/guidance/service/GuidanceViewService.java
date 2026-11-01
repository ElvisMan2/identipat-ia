package com.mnk.identipatia.guidance.service;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.analysis.service.AnalysisAccessContext;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.guidance.dto.GuidanceViewResponse;
import com.mnk.identipatia.guidance.model.GuidanceView;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;
import com.mnk.identipatia.guidance.repository.GuidanceViewRepository;
import com.mnk.identipatia.guidance.repository.ProtectionGuideRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class GuidanceViewService {
    private final GuidanceAnalysisResolver analysisResolver;
    private final ProtectionGuideRepository guideRepository;
    private final GuidanceViewRepository viewRepository;
    private final Clock clock;

    public GuidanceViewService(GuidanceAnalysisResolver analysisResolver,
            ProtectionGuideRepository guideRepository, GuidanceViewRepository viewRepository, Clock clock) {
        this.analysisResolver = analysisResolver;
        this.guideRepository = guideRepository;
        this.viewRepository = viewRepository;
        this.clock = clock;
    }

    @Transactional
    public GuidanceViewResponse record(AnalysisAccessContext access, UUID analysisId,
            String rawProtectionType, Long guideVersion) {
        if (access.mode() == AnalysisAccessContext.Mode.ADMIN_JWT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GUIDANCE_VIEW_STANDARD_ONLY",
                    "Only STANDARD sessions may record guidance views");
        }
        ProtectionType protectionType = parseProtectionType(rawProtectionType);
        if (guideVersion == null || guideVersion <= 0) {
            throw invalidRequest();
        }

        GuidanceAnalysisResolver.ResolvedAnalysis resolved = analysisResolver.resolve(access, analysisId);
        GuidanceAnalysisResolver.Option option = analysisResolver.requireOption(resolved, protectionType);
        if ("UNLIKELY".equals(option.applicability())) {
            throw new ApiException(HttpStatus.CONFLICT, "GUIDANCE_NEXT_STEPS_NOT_AVAILABLE",
                    "Next steps are not available for this modality");
        }

        var guide = guideRepository.findByProtectionTypeAndVersion(protectionType, guideVersion)
                .filter(value -> value.getStatus() == ProtectionGuideStatus.ACTIVE
                        || value.getStatus() == ProtectionGuideStatus.RETIRED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "GUIDANCE_VERSION_NOT_AVAILABLE",
                        "The requested guidance version is not available"));

        Instant candidateFirstViewedAt = clock.instant();
        viewRepository.insertIfAbsent(UUID.randomUUID(), analysisId, guide.getProtectionType().name(),
                guide.getVersion(), candidateFirstViewedAt);
        GuidanceView persisted = viewRepository.findByAnalysisIdAndProtectionTypeAndGuideVersion(
                        analysisId, protectionType, guideVersion)
                .orElseThrow(() -> new IllegalStateException("Guidance view was not persisted"));
        return new GuidanceViewResponse(persisted.getAnalysisId(), persisted.getProtectionType().name(),
                persisted.getGuideVersion(), persisted.getFirstViewedAt());
    }

    private static ProtectionType parseProtectionType(String value) {
        try {
            return ProtectionType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROTECTION_TYPE",
                    "Protection type is invalid");
        }
    }

    public static ApiException invalidRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GUIDANCE_VIEW_REQUEST",
                "guideVersion must be a positive integer");
    }
}

