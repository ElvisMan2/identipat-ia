package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;

public interface DiagnosticReportAssembler<T extends AnalysisResult> {
    DiagnosticReport assemble(Analysis analysis, T result, AnalysisReportContext context,
            ReportDisclaimer disclaimer);
}
