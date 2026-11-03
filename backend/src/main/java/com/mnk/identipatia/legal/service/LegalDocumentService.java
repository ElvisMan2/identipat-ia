package com.mnk.identipatia.legal.service;

import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.legal.dto.LegalDocumentResponse;
import com.mnk.identipatia.legal.dto.UpdateLegalDocumentRequest;
import com.mnk.identipatia.legal.html.LegalDocumentHtmlSanitizer;
import com.mnk.identipatia.legal.model.LegalDocument;
import com.mnk.identipatia.legal.model.LegalDocumentHistory;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class LegalDocumentService {
    private static final int MAX_TITLE_LENGTH = 255;

    private final LegalDocumentRepository documentRepository;
    private final LegalDocumentHistoryRepository historyRepository;
    private final LegalDocumentHtmlSanitizer sanitizer;
    private final LegalDocumentContentHasher hasher;
    private final UserRepository userRepository;
    private final Clock clock;

    public LegalDocumentService(LegalDocumentRepository documentRepository,
            LegalDocumentHistoryRepository historyRepository,
            LegalDocumentHtmlSanitizer sanitizer,
            LegalDocumentContentHasher hasher,
            UserRepository userRepository,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.sanitizer = sanitizer;
        this.hasher = hasher;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LegalDocumentResponse getCurrent() {
        return toResponse(requireCurrentEntity());
    }

    @Transactional(readOnly = true)
    public LegalDocument requireCurrentEntity() {
        return documentRepository.findCurrent().orElseThrow(LegalDocumentService::notFound);
    }

    @Transactional
    public LegalDocumentResponse update(UpdateLegalDocumentRequest request, Authentication authentication) {
        if (request == null || request.title() == null || request.contentHtml() == null) {
            throw invalidDocument();
        }
        String title = request.title().trim();
        String sanitizedHtml = sanitizer.sanitize(request.contentHtml());
        if (title.isBlank() || title.length() > MAX_TITLE_LENGTH || !sanitizer.hasVisibleText(sanitizedHtml)) {
            throw invalidDocument();
        }

        String contentHash = hasher.sha256(title, sanitizedHtml);
        LegalDocument existing = documentRepository.findCurrentForUpdate().orElse(null);
        if (existing != null && contentHash.equals(existing.getContentSha256())) {
            return toResponse(existing);
        }

        User admin = requireAdmin(authentication);
        // PostgreSQL stores TIMESTAMPTZ with microsecond precision. Normalize before
        // returning the entity so a no-op update has the same timestamp over the wire.
        Instant now = nextEffectiveAt(existing);
        if (!historyRepository.existsById(contentHash)) {
            LegalDocumentHistory history = new LegalDocumentHistory();
            history.setContentSha256(contentHash);
            history.setTitle(title);
            history.setContentHtml(sanitizedHtml);
            history.setCapturedAt(now);
            historyRepository.save(history);
        }

        LegalDocument document = existing == null ? new LegalDocument() : existing;
        if (existing == null) {
            document.setDocumentId(UUID.randomUUID());
        }
        document.setTitle(title);
        document.setContentHtml(sanitizedHtml);
        document.setContentSha256(contentHash);
        document.setUpdatedBy(admin.getUserId());
        document.setUpdatedAt(now);
        return toResponse(documentRepository.save(document));
    }

    private Instant nextEffectiveAt(LegalDocument existing) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (existing == null || now.isAfter(existing.getUpdatedAt())) {
            return now;
        }
        return existing.getUpdatedAt().plus(1, ChronoUnit.MICROS);
    }

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                    "A valid active ADMIN Bearer token is required");
        }
        return userRepository.findByDoi(authentication.getName())
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getUserType()))
                .filter(user -> "A".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_REQUIRED",
                        "A valid active ADMIN Bearer token is required"));
    }

    private static LegalDocumentResponse toResponse(LegalDocument document) {
        return new LegalDocumentResponse(document.getDocumentId(), document.getTitle(),
                document.getContentHtml(), document.getContentSha256(), document.getUpdatedAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND",
                "Legal document is not configured");
    }

    private static ApiException invalidDocument() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEGAL_DOCUMENT",
                "A title and legal HTML with visible text are required");
    }
}

