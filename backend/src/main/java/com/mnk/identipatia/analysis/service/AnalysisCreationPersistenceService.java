package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInput;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.model.ConsentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AnalysisCreationPersistenceService {
    private final AnalysisRepository analysisRepository;
    private final AnalysisInputRepository inputRepository;
    private final Clock clock;

    public AnalysisCreationPersistenceService(
            AnalysisRepository analysisRepository,
            AnalysisInputRepository inputRepository,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.inputRepository = inputRepository;
        this.clock = clock;
    }

    @Transactional
    public AnalysisCreatedResponse create(
            AnalysisAccessContext access,
            AnalysisInputType inputType,
            String originalText,
            String processedText,
            JsonNode sourceMetadata,
            ConsentEvent consent) {
        Instant now = clock.instant();
        UUID analysisId = UUID.randomUUID();

        Analysis analysis = new Analysis();
        analysis.setAnalysisId(analysisId);
        analysis.setUserId(access.user().getUserId());
        analysis.setSessionId(access.session() == null ? null : access.session().getSessionId());
        analysis.setConsentEventId(consent == null ? null : consent.getConsentEventId());
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
        inputRepository.save(input);

        return new AnalysisCreatedResponse(analysisId, AnalysisStatus.RECEIVED, now);
    }
}
