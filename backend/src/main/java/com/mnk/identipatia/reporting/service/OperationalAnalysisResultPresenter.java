package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.reporting.dto.OperationalModalityApplicability;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationalAnalysisResultPresenter {

    public PresentedResult present(AnalysisResult result) {
        if (result instanceof AnalysisResultV1 v1) {
            return presented(
                    v1.summary(),
                    v1.protectionOptions().stream()
                            .map(option -> option.type().name())
                            .toList(),
                    v1.protectionOptions().stream()
                            .map(option -> option.applicability().name())
                            .toList());
        }
        if (result instanceof AnalysisResultV2 v2) {
            return presented(
                    v2.summary(),
                    v2.protectionOptions().stream()
                            .map(option -> option.type().name())
                            .toList(),
                    v2.protectionOptions().stream()
                            .map(option -> option.applicability().name())
                            .toList());
        }
        throw new OperationalAnalysisDatasetException("Unsupported in-memory analysis result type");
    }

    private static PresentedResult presented(
            String summary, List<String> protectionTypes, List<String> applicabilityValues) {
        List<String> modalities = protectionTypes.stream()
                .map(DiagnosticReportPresentation::protectionLabel)
                .toList();
        List<OperationalModalityApplicability> applicability = java.util.stream.IntStream
                .range(0, protectionTypes.size())
                .mapToObj(index -> new OperationalModalityApplicability(
                        modalities.get(index),
                        DiagnosticReportPresentation.applicabilityLabel(
                                applicabilityValues.get(index))))
                .toList();
        return new PresentedResult(
                DiagnosticReportPresentation.narrative(summary), modalities, applicability);
    }

    public record PresentedResult(
            String conclusion,
            List<String> modalities,
            List<OperationalModalityApplicability> applicability) {
        public PresentedResult {
            modalities = List.copyOf(modalities);
            applicability = List.copyOf(applicability);
        }
    }
}
