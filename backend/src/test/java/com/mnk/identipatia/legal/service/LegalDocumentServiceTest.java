package com.mnk.identipatia.legal.service;

import com.mnk.identipatia.legal.dto.LegalDocumentResponse;
import com.mnk.identipatia.legal.dto.UpdateLegalDocumentRequest;
import com.mnk.identipatia.legal.html.LegalDocumentHtmlSanitizer;
import com.mnk.identipatia.legal.model.LegalDocument;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalDocumentServiceTest {

    @Test
    void differentContentsRemainStrictlyMonotonicWhenClockReturnsTheSameInstant() {
        LegalDocumentRepository documentRepository = mock(LegalDocumentRepository.class);
        LegalDocumentHistoryRepository historyRepository = mock(LegalDocumentHistoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        Authentication authentication = mock(Authentication.class);
        Instant fixedInstant = Instant.parse("2026-11-02T12:00:00.123456Z");
        LegalDocumentService service = new LegalDocumentService(
                documentRepository,
                historyRepository,
                new LegalDocumentHtmlSanitizer(),
                new LegalDocumentContentHasher(),
                userRepository,
                Clock.fixed(fixedInstant, ZoneOffset.UTC));

        User admin = new User();
        admin.setUserId(42L);
        admin.setDoi("ADMIN-42");
        admin.setUserType("ADMIN");
        admin.setStatus("A");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(admin.getDoi());
        when(userRepository.findByDoi(admin.getDoi())).thenReturn(Optional.of(admin));
        when(historyRepository.existsById(any())).thenReturn(false);
        when(documentRepository.save(any(LegalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.findCurrentForUpdate()).thenReturn(Optional.empty());

        LegalDocumentResponse first = service.update(
                new UpdateLegalDocumentRequest("A", "<p>Content A</p>"), authentication);
        LegalDocument persisted = new LegalDocument();
        persisted.setDocumentId(first.documentId());
        persisted.setTitle(first.title());
        persisted.setContentHtml(first.contentHtml());
        persisted.setContentSha256(first.contentHash());
        persisted.setUpdatedBy(admin.getUserId());
        persisted.setUpdatedAt(first.updatedAt());
        when(documentRepository.findCurrentForUpdate()).thenReturn(Optional.of(persisted));

        LegalDocumentResponse second = service.update(
                new UpdateLegalDocumentRequest("B", "<p>Content B</p>"), authentication);

        assertThat(first.updatedAt()).isEqualTo(fixedInstant);
        assertThat(second.updatedAt()).isEqualTo(fixedInstant.plus(1, ChronoUnit.MICROS));
        assertThat(second.updatedAt()).isAfter(first.updatedAt());
        assertThat(second.documentId()).isEqualTo(first.documentId());
    }
}
