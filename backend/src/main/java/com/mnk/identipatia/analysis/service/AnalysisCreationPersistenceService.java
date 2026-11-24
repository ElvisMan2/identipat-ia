package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInput;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisPdfDocument;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisPdfDocumentRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AnalysisCreationPersistenceService {
    private final AnalysisRepository analysisRepository;
    private final AnalysisInputRepository inputRepository;
    private final AnalysisPdfDocumentRepository pdfDocumentRepository;
    private final Clock clock;

    public AnalysisCreationPersistenceService(
            AnalysisRepository analysisRepository,
            AnalysisInputRepository inputRepository,
            AnalysisPdfDocumentRepository pdfDocumentRepository,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.inputRepository = inputRepository;
        this.pdfDocumentRepository = pdfDocumentRepository;
        this.clock = clock;
    }

    @Transactional
    public AnalysisCreatedResponse create(
            AnalysisAccessContext access,
            AnalysisInputType inputType,
            String originalText,
            String processedText,
            JsonNode sourceMetadata,
            UserTermsAcceptance acceptance) {
        return create(access, inputType, originalText, processedText, sourceMetadata, acceptance, null);
    }

    @Transactional
    public AnalysisCreatedResponse create(
            AnalysisAccessContext access,
            AnalysisInputType inputType,
            String originalText,
            String processedText,
            JsonNode sourceMetadata,
            UserTermsAcceptance acceptance,
            PdfDocumentPayload pdfDocument) {
        // PostgreSQL TIMESTAMPTZ is microsecond-precise. Returning the same
        // precision on the first 202 keeps a durable replay byte-for-byte stable.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID analysisId = UUID.randomUUID();

        Analysis analysis = new Analysis();
        analysis.setAnalysisId(analysisId);
        analysis.setUserId(access.user().getUserId());
        analysis.setSessionId(access.session() == null ? null : access.session().getSessionId());
        analysis.setTermsAcceptanceId(acceptance == null ? null : acceptance.getAcceptanceId());
        analysis.setInputType(inputType);
        analysis.setStatus(AnalysisStatus.RECEIVED);
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        analysisRepository.save(analysis);

        AnalysisInput input = new AnalysisInput();
        input.setAnalysisId(analysisId);
        input.setOriginalText(originalText);
        input.setProcessedText(processedText);
        input.setSourceMetadata(sourceMetadata);
        input.setCreatedAt(now);
        input.setProcessedAt(now);
        inputRepository.saveAndFlush(input);

        if ((inputType == AnalysisInputType.PDF) != (pdfDocument != null)) {
            throw new IllegalArgumentException("PDF analyses require exactly one durable PDF document");
        }
        if (pdfDocument != null) {
            AnalysisPdfDocument document = new AnalysisPdfDocument();
            document.setAnalysisId(analysisId);
            document.setContent(pdfDocument.content());
            document.setMediaType(pdfDocument.mediaType());
            document.setSizeBytes(pdfDocument.content().length);
            document.setSha256(pdfDocument.sha256());
            document.setCreatedAt(now);
            pdfDocumentRepository.saveAndFlush(document);
        }

        return new AnalysisCreatedResponse(analysisId, AnalysisStatus.RECEIVED, now);
    }

    public record PdfDocumentPayload(byte[] content, String mediaType, String sha256) {
        public PdfDocumentPayload {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
