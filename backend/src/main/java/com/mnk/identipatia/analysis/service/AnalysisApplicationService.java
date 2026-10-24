package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiProperties;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.dto.AnalysisFailureResponse;
import com.mnk.identipatia.analysis.dto.AnalysisResponse;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisFailureCode;
import com.mnk.identipatia.analysis.model.AnalysisInput;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.config.StandardSessionProperties;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.ConsentEvent;
import com.mnk.identipatia.repository.ConsentEventRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AnalysisApplicationService {
    private static final String PUBLIC_TEMPORARY_CODE = "ANALYSIS_TEMPORARILY_UNAVAILABLE";
    private static final String PUBLIC_TEMPORARY_MESSAGE = "No fue posible completar el análisis en este momento.";

    private final AnalysisRepository analysisRepository;
    private final AnalysisInputRepository inputRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final ConsentEventRepository consentRepository;
    private final StandardSessionProperties sessionProperties;
    private final AnalysisProperties analysisProperties;
    private final AiProperties aiProperties;
    private final ObjectProvider<GenerativeAiProvider> provider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnalysisApplicationService(
            AnalysisRepository analysisRepository,
            AnalysisInputRepository inputRepository,
            StoredAnalysisResultRepository resultRepository,
            ConsentEventRepository consentRepository,
            StandardSessionProperties sessionProperties,
            AnalysisProperties analysisProperties,
            AiProperties aiProperties,
            ObjectProvider<GenerativeAiProvider> provider,
            ObjectMapper objectMapper,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.inputRepository = inputRepository;
        this.resultRepository = resultRepository;
        this.consentRepository = consentRepository;
        this.sessionProperties = sessionProperties;
        this.analysisProperties = analysisProperties;
        this.aiProperties = aiProperties;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AnalysisCreatedResponse createText(AnalysisAccessContext access, String originalText) {
        ConsentEvent consent = access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION
                ? requireCurrentConsent(access) : null;
        ensureAiAvailable();

        String processedText = TextNormalizer.normalize(originalText);
        validateLength(processedText);
        return persistText(access, consent, originalText, processedText);
    }

    private ConsentEvent requireCurrentConsent(AnalysisAccessContext access) {
        return consentRepository
                .findBySessionSessionIdAndUserUserIdAndConsentVersionAndConsentDocumentHashAndDecision(
                        access.session().getSessionId(), access.user().getUserId(),
                        sessionProperties.getConsentCurrentVersion(),
                        sessionProperties.getConsentDocumentSha256(), ConsentDecision.ACCEPTED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CONSENT_REQUIRED",
                        "Current consent must be accepted before creating an analysis"));
    }

    private AnalysisCreatedResponse persistText(AnalysisAccessContext access, ConsentEvent consent,
            String originalText, String processedText) {
        Instant now = clock.instant();
        UUID analysisId = UUID.randomUUID();

        Analysis analysis = new Analysis();
        analysis.setAnalysisId(analysisId);
        analysis.setUserId(access.user().getUserId());
        analysis.setSessionId(access.session() == null ? null : access.session().getSessionId());
        analysis.setConsentEventId(consent == null ? null : consent.getConsentEventId());
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setStatus(AnalysisStatus.RECEIVED);
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        analysisRepository.save(analysis);

        AnalysisInput input = new AnalysisInput();
        input.setAnalysisId(analysisId);
        input.setOriginalText(originalText);
        input.setProcessedText(processedText);
        input.setSourceMetadata(objectMapper.createObjectNode());
        input.setCreatedAt(now);
        input.setProcessedAt(now);
        inputRepository.save(input);

        return new AnalysisCreatedResponse(analysisId, AnalysisStatus.RECEIVED, now);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(AnalysisAccessContext access, UUID analysisId) {
        Analysis analysis = (access.mode() == AnalysisAccessContext.Mode.ADMIN_JWT
                ? analysisRepository.findByAnalysisIdAndUserIdAndSessionIdIsNull(
                        analysisId, access.user().getUserId())
                : analysisRepository.findByAnalysisIdAndSessionId(
                        analysisId, access.session().getSessionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                        "Analysis was not found"));

        AnalysisResult result = null;
        AnalysisFailureResponse failure = null;
        if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
            StoredAnalysisResult stored = resultRepository.findById(analysisId)
                    .orElseThrow(() -> new IllegalStateException("Completed analysis has no result"));
            try {
                result = new AnalysisResultReader(objectMapper)
                        .read(stored.getSchemaVersion(), stored.getResultJson());
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new IllegalStateException("Stored analysis result is invalid", exception);
            }
        } else if (analysis.getStatus() == AnalysisStatus.FAILED) {
            failure = publicFailure(analysis.getFailureCode());
        }
        return new AnalysisResponse(analysis.getAnalysisId(), analysis.getInputType(), analysis.getStatus(),
                analysis.getCreatedAt(), analysis.getStartedAt(), analysis.getCompletedAt(), analysis.getFailedAt(),
                result, failure);
    }

    private void ensureAiAvailable() {
        if (!aiProperties.enabled() || provider.orderedStream().findFirst().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS_AI_UNAVAILABLE",
                    "Analysis is temporarily unavailable");
        }
    }

    private void validateLength(String text) {
        int length = text == null ? 0 : text.codePointCount(0, text.length());
        if (length < analysisProperties.textMinLength() || length > analysisProperties.textMaxLength()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ANALYSIS_DESCRIPTION",
                    "Analysis description is outside the configured limits");
        }
    }

    private static AnalysisFailureResponse publicFailure(String internalCode) {
        AnalysisFailureCode code;
        try {
            code = AnalysisFailureCode.valueOf(internalCode);
        } catch (RuntimeException exception) {
            return new AnalysisFailureResponse("ANALYSIS_INTERNAL_ERROR", PUBLIC_TEMPORARY_MESSAGE);
        }
        return switch (code) {
            case AI_TIMEOUT, AI_RATE_LIMITED, AI_PROVIDER_UNAVAILABLE, AI_PROVIDER_ERROR ->
                    new AnalysisFailureResponse(PUBLIC_TEMPORARY_CODE, PUBLIC_TEMPORARY_MESSAGE);
            case AI_INVALID_RESPONSE -> new AnalysisFailureResponse(
                    "ANALYSIS_INVALID_RESULT", "El proveedor devolvió un resultado que no pudo ser validado.");
            case AI_AUTHENTICATION, AI_INVALID_REQUEST -> new AnalysisFailureResponse(
                    "ANALYSIS_CONFIGURATION_ERROR", "El análisis no está disponible por un problema de configuración.");
            case WORKER_PROCESSING_ERROR -> new AnalysisFailureResponse(
                    "ANALYSIS_INTERNAL_ERROR", PUBLIC_TEMPORARY_MESSAGE);
        };
    }
}
