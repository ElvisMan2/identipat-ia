package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OperationalDiagnosticReportTextRenderer {
    public String render(DiagnosticReport report) {
        List<String> sections = new ArrayList<>();
        sections.add("Resumen:\n" + report.summary());

        if (!report.protectionOptions().isEmpty()) {
            sections.add("Modalidades de protección:\n" + report.protectionOptions().stream()
                    .map(option -> "- " + option.name() + " — " + option.applicabilityLabel())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElseThrow());
        }

        if (report.patentScreening() != null) {
            sections.add("Revisión de patente:\n" + renderScreening(report.patentScreening()));
        }

        if (report.historicalPatentability() != null) {
            sections.add("Revisión de patente:\n"
                    + report.historicalPatentability().rationale());
        }

        if (report.geneticResourceAccess() != null) {
            sections.add("Recursos genéticos:\n" + renderGenetic(report.geneticResourceAccess()));
        }

        addList(sections, "Observaciones", report.observations());
        addList(sections, "Advertencias", report.warnings());
        sections.add("Disclaimer:\n" + report.disclaimer().title() + ": "
                + report.disclaimer().content());
        return String.join("\n\n", sections);
    }

    private static String renderScreening(DiagnosticReport.PatentScreeningPresentation screening) {
        List<String> lines = new ArrayList<>();
        lines.add(screening.title());
        lines.add(screening.context());
        addScreeningArticle(lines, screening.article15());
        addScreeningArticle(lines, screening.article20());
        lines.add(screening.scopeNote());
        return String.join("\n", lines);
    }

    private static void addScreeningArticle(List<String> lines,
            DiagnosticReport.ScreeningArticlePresentation article) {
        lines.add(article.article() + " — " + article.assessmentLabel());
        lines.add(article.rationale());
        article.matches().forEach(match ->
                lines.add("- " + match.literal() + ": " + match.rationale()));
    }

    private static String renderGenetic(DiagnosticReport.GeneticResourcePresentation genetic) {
        List<String> lines = new ArrayList<>();
        lines.add(genetic.assessmentLabel());
        lines.add(genetic.rationale());
        if (!genetic.missingInformation().isEmpty()) {
            lines.add("Información faltante:");
            genetic.missingInformation().forEach(value -> lines.add("- " + value));
        }
        return String.join("\n", lines);
    }

    private static void addList(List<String> sections, String title, List<String> values) {
        if (!values.isEmpty()) {
            sections.add(title + ":\n" + values.stream()
                    .map(value -> "- " + value)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElseThrow());
        }
    }
}
