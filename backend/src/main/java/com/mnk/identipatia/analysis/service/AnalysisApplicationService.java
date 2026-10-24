package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiProperties;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.dto.AnalysisFailureResponse;
import com.mnk.identipatia.analysis.dto.AnalysisResponse;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisFailureCode;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.config.StandardSessionProperties;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.ConsentEvent;
import com.mnk.identipatia.preprocessing.client.PdfExtractionResponse;
import com.mnk.identipatia.preprocessing.client.AudioTranscriptionResponse;
import com.mnk.identipatia.preprocessing.client.PreprocessingAudioException;
import com.mnk.identipatia.preprocessing.client.PreprocessingPdfException;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceUnavailableException;
import com.mnk.identipatia.repository.ConsentEventRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalysisApplicationService {
    private static final String PUBLIC_TEMPORARY_CODE = "ANALYSIS_TEMPORARILY_UNAVAILABLE";
    private static final String PUBLIC_TEMPORARY_MESSAGE = "No fue posible completar el análisis en este momento.";
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final Set<String> SUPPORTED_AUDIO_MEDIA_TYPES = Set.of(
            "audio/webm",
            "audio/mpeg",
            "audio/mp4",
            "audio/x-m4a",
            "audio/wav",
            "audio/x-wav",
            "audio/ogg",
            "audio/flac");

    private final AnalysisRepository analysisRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final ConsentEventRepository consentRepository;
    private final StandardSessionProperties sessionProperties;
    private final AnalysisProperties analysisProperties;
    private final AiProperties aiProperties;
    private final ObjectProvider<GenerativeAiProvider> provider;
    private final ObjectMapper objectMapper;
    private final PreprocessingServiceClient preprocessingClient;
    private final AnalysisCreationPersistenceService creationService;

    public AnalysisApplicationService(
            AnalysisRepository analysisRepository,
            StoredAnalysisResultRepository resultRepository,
            ConsentEventRepository consentRepository,
            StandardSessionProperties sessionProperties,
            AnalysisProperties analysisProperties,
            AiProperties aiProperties,
            ObjectProvider<GenerativeAiProvider> provider,
            ObjectMapper objectMapper,
            PreprocessingServiceClient preprocessingClient,
            AnalysisCreationPersistenceService creationService) {
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.consentRepository = consentRepository;
        this.sessionProperties = sessionProperties;
        this.analysisProperties = analysisProperties;
        this.aiProperties = aiProperties;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.preprocessingClient = preprocessingClient;
        this.creationService = creationService;
    }

    @Transactional
    public AnalysisCreatedResponse createText(AnalysisAccessContext access, String originalText) {
        ConsentEvent consent = access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION
                ? requireCurrentConsent(access) : null;
        ensureAiAvailable();

        String processedText = TextNormalizer.normalize(originalText);
        validateTextLength(processedText);
        return creationService.create(access, AnalysisInputType.TEXT, originalText, processedText,
                objectMapper.createObjectNode(), consent);
    }

    public AnalysisCreatedResponse createPdf(AnalysisAccessContext access, MultipartFile file) {
        ConsentEvent consent = access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION
                ? requireCurrentConsent(access) : null;
        byte[] pdfBytes = validateAndReadPdf(file);

        PdfExtractionResponse extraction;
        try {
            extraction = preprocessingClient.extractPdf(pdfBytes);
        } catch (PreprocessingPdfException exception) {
            throw mapPdfError(exception.getCode());
        } catch (PreprocessingServiceUnavailableException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PREPROCESSING_SERVICE_UNAVAILABLE",
                    "PDF preprocessing is temporarily unavailable");
        }

        if (extraction.pageCount() > analysisProperties.pdf().maxPages()) {
            throw pdfError("PDF_PAGE_LIMIT_EXCEEDED", "The PDF exceeds the configured page limit");
        }
        String processedText = TextNormalizer.normalize(extraction.text());
        validatePdfLength(processedText);
        ensureAiAvailable();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("mediaType", "application/pdf");
        metadata.put("sizeBytes", pdfBytes.length);
        metadata.put("pageCount", extraction.pageCount());
        metadata.put("characterCount", extraction.characterCount());
        metadata.put("extractionMethod", extraction.extractionMethod());
        metadata.put("sha256", sha256(pdfBytes));
        return creationService.create(access, AnalysisInputType.PDF, extraction.text(), processedText,
                metadata, consent);
    }

    public AnalysisCreatedResponse createAudio(AnalysisAccessContext access, MultipartFile file) {
        ConsentEvent consent = access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION
                ? requireCurrentConsent(access) : null;
        ValidatedAudio audio = validateAndReadAudio(file);

        AudioTranscriptionResponse transcription;
        try {
            transcription = preprocessingClient.transcribeAudio(audio.bytes(), audio.mediaType());
        } catch (PreprocessingAudioException exception) {
            throw mapAudioError(exception.getCode());
        } catch (PreprocessingServiceUnavailableException exception) {
            throw audioUnavailable();
        }

        if (transcription.durationSeconds() > analysisProperties.audio().maxDurationSeconds()) {
            throw audioError("AUDIO_DURATION_LIMIT_EXCEEDED",
                    "The audio exceeds the configured duration limit");
        }
        String processedText = TextNormalizer.normalize(transcription.text());
        validateAudioLength(processedText);
        ensureAiAvailable();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("mediaType", audio.mediaType());
        metadata.put("sizeBytes", audio.bytes().length);
        metadata.put("durationSeconds", transcription.durationSeconds());
        metadata.put("characterCount", transcription.characterCount());
        metadata.put("language", transcription.language());
        metadata.put("transcriptionMethod", transcription.transcriptionMethod());
        metadata.put("model", transcription.model());
        metadata.put("sha256", sha256(audio.bytes()));
        return creationService.create(access, AnalysisInputType.AUDIO, transcription.text(), processedText,
                metadata, consent);
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

    private void validateTextLength(String text) {
        int length = text == null ? 0 : text.codePointCount(0, text.length());
        if (length < analysisProperties.textMinLength() || length > analysisProperties.textMaxLength()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ANALYSIS_DESCRIPTION",
                    "Analysis description is outside the configured limits");
        }
    }

    private void validatePdfLength(String text) {
        int length = text == null ? 0 : text.codePointCount(0, text.length());
        if (length < analysisProperties.textMinLength()) {
            throw pdfError("PDF_TEXT_NOT_EXTRACTABLE",
                    "The PDF does not contain enough extractable text");
        }
        if (length > analysisProperties.textMaxLength()) {
            throw pdfError("PDF_TEXT_TOO_LONG", "The extracted PDF text exceeds the configured limit");
        }
    }

    private void validateAudioLength(String text) {
        int length = text == null ? 0 : text.codePointCount(0, text.length());
        if (length < analysisProperties.textMinLength()) {
            throw audioError("AUDIO_TRANSCRIPTION_EMPTY",
                    "The audio did not produce enough transcribed text");
        }
        if (length > analysisProperties.textMaxLength()) {
            throw audioError("AUDIO_TEXT_TOO_LONG",
                    "The transcribed audio text exceeds the configured limit");
        }
    }

    private byte[] validateAndReadPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw pdfError("INVALID_PDF", "A non-empty PDF file is required");
        }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_PDF_MEDIA_TYPE",
                    "Only application/pdf is supported");
        }
        if (file.getSize() > analysisProperties.pdf().maxFileSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF_TOO_LARGE",
                    "The PDF exceeds the configured size limit");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > analysisProperties.pdf().maxFileSizeBytes()) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF_TOO_LARGE",
                        "The PDF exceeds the configured size limit");
            }
            if (!hasPdfSignature(bytes)) {
                throw pdfError("INVALID_PDF", "The uploaded file is not a valid PDF");
            }
            return bytes;
        } catch (IOException exception) {
            throw pdfError("INVALID_PDF", "The PDF could not be read");
        }
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private ValidatedAudio validateAndReadAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw audioError("INVALID_AUDIO", "A non-empty audio file is required");
        }
        String mediaType = normalizeMediaType(file.getContentType());
        if (!SUPPORTED_AUDIO_MEDIA_TYPES.contains(mediaType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO_MEDIA_TYPE",
                    "The audio media type is not supported");
        }
        if (file.getSize() > analysisProperties.audio().maxFileSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE",
                    "The audio exceeds the configured size limit");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > analysisProperties.audio().maxFileSizeBytes()) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE",
                        "The audio exceeds the configured size limit");
            }
            return new ValidatedAudio(bytes, mediaType);
        } catch (IOException exception) {
            throw audioError("INVALID_AUDIO", "The audio could not be read");
        }
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        String baseType = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException mapPdfError(String code) {
        return switch (code) {
            case "PDF_ENCRYPTED" -> pdfError("PDF_ENCRYPTED_NOT_SUPPORTED",
                    "Encrypted or password-protected PDFs are not supported");
            case "PDF_PAGE_LIMIT_EXCEEDED" -> pdfError(code,
                    "The PDF exceeds the configured page limit");
            case "PDF_TEXT_NOT_EXTRACTABLE" -> pdfError(code,
                    "The PDF does not contain extractable text");
            case "INVALID_PDF" -> pdfError(code, "The PDF could not be read");
            case "PDF_TOO_LARGE" -> new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, code,
                    "The PDF exceeds the configured size limit");
            case "UNSUPPORTED_PDF_MEDIA_TYPE" -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code,
                    "Only application/pdf is supported");
            default -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PREPROCESSING_SERVICE_UNAVAILABLE",
                    "PDF preprocessing is temporarily unavailable");
        };
    }

    private static ApiException pdfError(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static ApiException mapAudioError(String code) {
        return switch (code) {
            case "AUDIO_TOO_LARGE" -> new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, code,
                    "The audio exceeds the configured size limit");
            case "UNSUPPORTED_AUDIO_MEDIA_TYPE" -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code,
                    "The audio media type is not supported");
            case "INVALID_AUDIO" -> audioError(code, "The audio could not be decoded");
            case "AUDIO_DURATION_LIMIT_EXCEEDED" -> audioError(code,
                    "The audio exceeds the configured duration limit");
            case "AUDIO_TRANSCRIPTION_EMPTY" -> audioError(code,
                    "The audio did not produce enough transcribed text");
            case "AUDIO_MODEL_NOT_READY", "AUDIO_TRANSCRIPTION_ERROR" -> audioUnavailable();
            default -> audioUnavailable();
        };
    }

    private static ApiException audioUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_TRANSCRIPTION_UNAVAILABLE",
                "Audio transcription is temporarily unavailable");
    }

    private static ApiException audioError(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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

    private record ValidatedAudio(byte[] bytes, String mediaType) {
    }
}
