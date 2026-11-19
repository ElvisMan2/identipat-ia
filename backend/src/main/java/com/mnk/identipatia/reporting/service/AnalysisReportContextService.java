package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import com.mnk.identipatia.reporting.repository.ReportDisclaimerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnalysisReportContextService {
    public static final String REPORT_VERSION = "diagnostic-report/1.0";
    public static final String REPORT_VERSION_1_1 = "diagnostic-report/1.1";

    private final ReportDisclaimerRepository disclaimerRepository;
    private final AnalysisReportContextRepository contextRepository;

    public AnalysisReportContextService(ReportDisclaimerRepository disclaimerRepository,
            AnalysisReportContextRepository contextRepository) {
        this.disclaimerRepository = disclaimerRepository;
        this.contextRepository = contextRepository;
    }

    /** Called inside the terminal analysis transaction. The shared row lock serializes activation and completion. */
    public AnalysisReportContext bindToActiveDisclaimer(UUID analysisId, Instant now, String resultSchemaVersion) {
        String reportVersion = reportVersionForResult(resultSchemaVersion);
        ReportDisclaimer active = disclaimerRepository.lockAllInStableOrder().stream()
                .filter(value -> value.getStatus() == ReportDisclaimerStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exactly one ACTIVE report disclaimer is required"));
        AnalysisReportContext context = new AnalysisReportContext();
        context.setAnalysisId(analysisId);
        context.setDisclaimerId(active.getDisclaimerId());
        context.setReportVersion(reportVersion);
        context.setCreatedAt(now);
        return contextRepository.save(context);
    }

    public static String reportVersionForResult(String resultSchemaVersion) {
        return switch (resultSchemaVersion == null ? "" : resultSchemaVersion) {
            case AnalysisResultV1.SCHEMA_VERSION, AnalysisResultV2.SCHEMA_VERSION -> REPORT_VERSION;
            case AnalysisResultV3.SCHEMA_VERSION -> REPORT_VERSION_1_1;
            default -> throw new IllegalArgumentException(
                    "Unsupported analysis result schema version for reporting: " + resultSchemaVersion);
        };
    }
}
