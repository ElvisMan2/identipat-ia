package com.mnk.identipatia.legal.service;

import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.legal.model.LegalDocument;
import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;
import com.mnk.identipatia.legal.repository.UserTermsAcceptanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TermsAcceptanceService {
    public static final String STANDARD_REGISTRATION = "STANDARD_REGISTRATION";
    public static final String STANDARD_WEB = "STANDARD_WEB";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final LegalDocumentRepository documentRepository;
    private final UserTermsAcceptanceRepository acceptanceRepository;
    private final Clock clock;

    public TermsAcceptanceService(LegalDocumentRepository documentRepository,
            UserTermsAcceptanceRepository acceptanceRepository, Clock clock) {
        this.documentRepository = documentRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentAcceptance(Long userId) {
        return documentRepository.findCurrent()
                .flatMap(document -> findForCurrentEffectivePeriod(userId, document))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public UserTermsAcceptance requireCurrentAcceptance(Long userId) {
        LegalDocument document = documentRepository.findCurrent().orElseThrow(TermsAcceptanceService::consentRequired);
        return findForCurrentEffectivePeriod(userId, document)
                .orElseThrow(TermsAcceptanceService::consentRequired);
    }

    @Transactional(readOnly = true)
    public LegalDocument requireMatchingCurrent(String contentHash) {
        if (contentHash == null || !SHA256.matcher(contentHash).matches()) {
            throw documentChanged();
        }
        LegalDocument document = documentRepository.findCurrent()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND",
                        "Legal document is not configured"));
        if (!document.getContentSha256().equals(contentHash)) {
            throw documentChanged();
        }
        return document;
    }

    @Transactional
    public UserTermsAcceptance acceptCurrent(Long userId, String contentHash, String source) {
        LegalDocument document = requireMatchingCurrent(contentHash);
        Instant acceptedAt = clock.instant();
        acceptanceRepository.insertIfAbsent(UUID.randomUUID(), userId,
                document.getContentSha256(), document.getUpdatedAt(), acceptedAt, source);
        return findForCurrentEffectivePeriod(userId, document)
                .orElseThrow(() -> new IllegalStateException("Acceptance insert did not persist"));
    }

    private Optional<UserTermsAcceptance> findForCurrentEffectivePeriod(Long userId, LegalDocument document) {
        return acceptanceRepository.findByUserIdAndAcceptedContentSha256AndAcceptedDocumentUpdatedAt(
                userId, document.getContentSha256(), document.getUpdatedAt());
    }

    private static ApiException consentRequired() {
        return new ApiException(HttpStatus.CONFLICT, "CONSENT_REQUIRED",
                "Current legal content must be accepted before creating an analysis");
    }

    private static ApiException documentChanged() {
        return new ApiException(HttpStatus.CONFLICT, "LEGAL_DOCUMENT_CHANGED",
                "The legal content has changed; reload it before deciding");
    }
}

