package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiagnosticReportAssemblerV1 implements DiagnosticReportAssembler<AnalysisResultV1> {
    @Override
    public DiagnosticReport assemble(Analysis analysis, AnalysisResultV1 result, AnalysisReportContext context,
            ReportDisclaimer disclaimer) {
        DiagnosticReportPresentation.requireSupportedContext(context);
        List<DiagnosticReport.ProtectionPresentation> options = result.protectionOptions().stream()
                .map(option -> new DiagnosticReport.ProtectionPresentation(
                        option.type().name(),
                        DiagnosticReportPresentation.protectionLabel(option.type().name()),
                        DiagnosticReportPresentation.RELATIONSHIP_QUESTION,
                        option.applicability().name(),
                        DiagnosticReportPresentation.applicabilityLabel(option.applicability().name()),
                        null,
                        DiagnosticReportPresentation.narrative(option.rationale()),
                        List.of(),
                        DiagnosticReportPresentation.orientation(option.applicability().name()),
                        null))
                .toList();
        return new DiagnosticReport(context.getReportVersion(), analysis.getAnalysisId(), result.schemaVersion(),
                analysis.getInputType(), analysis.getCompletedAt(), DiagnosticReportPresentation.TITLE,
                DiagnosticReportPresentation.narrative(result.summary()),
                DiagnosticReportPresentation.APPLICABILITY_NOTE, options, null, null,
                new DiagnosticReport.HistoricalPatentabilityPresentation(
                        result.patentabilityAssessment().outcome().name(),
                        DiagnosticReportPresentation.narrative(result.patentabilityAssessment().rationale())),
                DiagnosticReportPresentation.narratives(result.observations()),
                DiagnosticReportPresentation.narratives(result.warnings()), DiagnosticReportPresentation.SCOPE,
                DiagnosticReportPresentation.disclaimer(disclaimer));
    }
}
