package com.mnk.identipatia.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.service.AnalysisAccessContext;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import com.mnk.identipatia.reporting.repository.ReportDisclaimerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AnalysisReportService {
    private final AnalysisRepository analysisRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final AnalysisReportContextRepository contextRepository;
    private final ReportDisclaimerRepository disclaimerRepository;
    private final ObjectMapper objectMapper;
    private final DiagnosticReportAssemblerV1 assemblerV1;
    private final DiagnosticReportAssemblerV2 assemblerV2;
    private final DiagnosticReportPdfRenderer pdfRenderer;

    public AnalysisReportService(AnalysisRepository analysisRepository,
            StoredAnalysisResultRepository resultRepository,
            AnalysisReportContextRepository contextRepository,
            ReportDisclaimerRepository disclaimerRepository,
            ObjectMapper objectMapper,
            DiagnosticReportAssemblerV1 assemblerV1,
            DiagnosticReportAssemblerV2 assemblerV2,
            DiagnosticReportPdfRenderer pdfRenderer) {
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.contextRepository = contextRepository;
        this.disclaimerRepository = disclaimerRepository;
        this.objectMapper = objectMapper;
        this.assemblerV1 = assemblerV1;
        this.assemblerV2 = assemblerV2;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional(readOnly = true)
    public DiagnosticReport get(AnalysisAccessContext access, UUID analysisId) {
        Analysis analysis = findOwned(access, analysisId);
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_UNAVAILABLE",
                    "A report is unavailable for a failed analysis");
        }
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_NOT_READY",
                    "The analysis is not completed yet");
        }
        AnalysisReportContext context = contextRepository.findById(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "REPORT_CONTEXT_UNAVAILABLE", "Historical report context is unavailable"));
        ReportDisclaimer disclaimer = disclaimerRepository.findById(context.getDisclaimerId())
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "REPORT_CONTEXT_UNAVAILABLE", "Historical report context is unavailable"));
        StoredAnalysisResult stored = resultRepository.findById(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "REPORT_CONTEXT_UNAVAILABLE", "Stored analysis result is unavailable"));
        AnalysisResult result = read(stored);
        if (result instanceof AnalysisResultV1 v1) {
            return assemblerV1.assemble(analysis, v1, context, disclaimer);
        }
        if (result instanceof AnalysisResultV2 v2) {
            return assemblerV2.assemble(analysis, v2, context, disclaimer);
        }
        throw unsupportedVersion();
    }

    public byte[] getPdf(AnalysisAccessContext access, UUID analysisId) {
        try {
            return pdfRenderer.render(get(access, analysisId));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_GENERATION_FAILED",
                    "The PDF report could not be generated");
        }
    }

    private Analysis findOwned(AnalysisAccessContext access, UUID analysisId) {
        return (access.mode() == AnalysisAccessContext.Mode.ADMIN_JWT
                ? analysisRepository.findByAnalysisIdAndUserIdAndSessionIdIsNull(
                        analysisId, access.user().getUserId())
                : analysisRepository.findByAnalysisIdAndSessionId(analysisId, access.session().getSessionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                        "Analysis was not found"));
    }

    private AnalysisResult read(StoredAnalysisResult stored) {
        try {
            return new AnalysisResultReader(objectMapper).read(stored.getSchemaVersion(), stored.getResultJson());
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Unsupported")) {
                throw unsupportedVersion();
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_CONTEXT_UNAVAILABLE",
                    "Stored analysis result is invalid");
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_CONTEXT_UNAVAILABLE",
                    "Stored analysis result is invalid");
        }
    }

    private static ApiException unsupportedVersion() {
        return new ApiException(HttpStatus.CONFLICT, "REPORT_UNSUPPORTED_RESULT_VERSION",
                "The stored analysis result version is not supported for reporting");
    }
}
