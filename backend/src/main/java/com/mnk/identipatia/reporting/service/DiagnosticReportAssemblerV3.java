package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAssessment;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.mnk.identipatia.analysis.result.v3.TraditionalKnowledgeAssessment;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticReportAssemblerV3 implements DiagnosticReportAssembler<AnalysisResultV3> {

    @Override
    public DiagnosticReport assemble(Analysis analysis, AnalysisResultV3 result, AnalysisReportContext context,
            ReportDisclaimer disclaimer) {
        DiagnosticReportPresentation.requireSupportedContext(context);
        DiagnosticReport.PatentScreeningPresentation screening = result.patentScreening().applicable()
                ? DiagnosticReportAssemblerV2.screening(result.patentScreening()) : null;
        DiagnosticReport.GeneticResourcePresentation genetic =
                result.geneticResourceAccess().assessment() == GeneticResourceAssessment.NOT_INDICATED
                        ? null : DiagnosticReportAssemblerV2.genetic(result.geneticResourceAccess());
        DiagnosticReport.TraditionalKnowledgePresentation traditional =
                result.traditionalKnowledgeAccess().assessment() == TraditionalKnowledgeAssessment.NOT_INDICATED
                        ? null : traditional(result);

        return new DiagnosticReport(context.getReportVersion(), analysis.getAnalysisId(), result.schemaVersion(),
                analysis.getInputType(), analysis.getCompletedAt(), DiagnosticReportPresentation.TITLE,
                DiagnosticReportPresentation.narrative(result.summary()),
                DiagnosticReportPresentation.APPLICABILITY_NOTE,
                result.protectionOptions().stream().map(DiagnosticReportAssemblerV2::option).toList(),
                screening, genetic, traditional, null,
                DiagnosticReportPresentation.narratives(result.observations()),
                DiagnosticReportPresentation.narratives(result.warnings()),
                DiagnosticReportPresentation.SCOPE, DiagnosticReportPresentation.disclaimer(disclaimer));
    }

    private static DiagnosticReport.TraditionalKnowledgePresentation traditional(AnalysisResultV3 result) {
        var access = result.traditionalKnowledgeAccess();
        return new DiagnosticReport.TraditionalKnowledgePresentation(
                access.assessment().name(),
                DiagnosticReportPresentation.accessAssessmentLabel(access.assessment().name()),
                DiagnosticReportPresentation.narrative(access.rationale()),
                DiagnosticReportPresentation.narratives(access.missingInformation()));
    }
}
