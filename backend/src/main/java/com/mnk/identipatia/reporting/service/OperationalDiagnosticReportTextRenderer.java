package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        screeningPresentations(report).forEach(screening ->
                sections.add("Revisión de patente:\n" + renderScreening(screening)));

        if (report.historicalPatentability() != null) {
            sections.add("Revisión de patente:\n"
                    + report.historicalPatentability().rationale());
        }

        geneticPresentations(report).forEach(genetic ->
                sections.add("Recursos genéticos:\n" + renderGenetic(genetic)));

        addList(sections, "Observaciones", report.observations());
        addList(sections, "Advertencias", report.warnings());
        sections.add("Disclaimer:\n" + report.disclaimer().title() + ": "
                + report.disclaimer().content());
        return String.join("\n\n", sections);
    }

    private static Set<DiagnosticReport.PatentScreeningPresentation> screeningPresentations(
            DiagnosticReport report) {
        Set<DiagnosticReport.PatentScreeningPresentation> presentations = new LinkedHashSet<>();
        report.protectionOptions().stream()
                .map(DiagnosticReport.ProtectionPresentation::patentScreening)
                .filter(java.util.Objects::nonNull)
                .forEach(presentations::add);
        if (report.patentScreening() != null) {
            presentations.add(report.patentScreening());
        }
        return presentations;
    }

    private static Set<DiagnosticReport.GeneticResourcePresentation> geneticPresentations(
            DiagnosticReport report) {
        Set<DiagnosticReport.GeneticResourcePresentation> presentations = new LinkedHashSet<>();
        report.protectionOptions().stream()
                .map(DiagnosticReport.ProtectionPresentation::geneticResourceAccess)
                .filter(java.util.Objects::nonNull)
                .forEach(presentations::add);
        if (report.geneticResourceAccess() != null) {
            presentations.add(report.geneticResourceAccess());
        }
        return presentations;
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
