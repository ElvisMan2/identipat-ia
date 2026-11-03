package com.mnk.identipatia;

import com.mnk.identipatia.legal.model.LegalDocument;
import com.mnk.identipatia.legal.model.LegalDocumentHistory;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class TestLegalDocumentFixture {
    public static final String TITLE = "Test legal document";
    public static final String HTML = "<p>Test legal content</p>";
    public static final String HASH = sha256(TITLE + "\n" + HTML);

    private TestLegalDocumentFixture() {
    }

    public static void ensureCurrent(LegalDocumentRepository documentRepository,
            LegalDocumentHistoryRepository historyRepository) {
        if (documentRepository.findCurrent().isPresent()) {
            return;
        }
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        LegalDocumentHistory history = new LegalDocumentHistory();
        history.setContentSha256(HASH);
        history.setTitle(TITLE);
        history.setContentHtml(HTML);
        history.setCapturedAt(now);
        historyRepository.saveAndFlush(history);

        LegalDocument document = new LegalDocument();
        document.setDocumentId(UUID.randomUUID());
        document.setTitle(TITLE);
        document.setContentHtml(HTML);
        document.setContentSha256(HASH);
        document.setUpdatedAt(now);
        documentRepository.saveAndFlush(document);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
