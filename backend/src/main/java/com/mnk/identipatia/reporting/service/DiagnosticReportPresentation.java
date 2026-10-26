package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiagnosticReportPresentation {
    static final String TITLE = "Reporte orientativo de identificación de propiedad intelectual";
    static final String RELATIONSHIP_QUESTION = "¿Cómo se relaciona con tu creación?";
    static final String APPLICABILITY_NOTE = "La relación mostrada indica qué tan directamente lo descrito en tu creación se vincula con esta modalidad de protección. No representa una probabilidad de registro, concesión o reconocimiento definitivo de un derecho.";
    static final String SCREENING_NOTE = "Este screening se limita a posibles coincidencias con los artículos 15 y 20 de la Decisión 486 y no constituye un examen completo de patentabilidad.";
    static final String SCOPE = "Este análisis identifica relaciones orientativas a partir de la información disponible. No determina registrabilidad, concesión o reconocimiento definitivo, ni sustituye una evaluación especializada o una decisión oficial.";
    private static final Map<String, String> NARRATIVE_PROTECTION_LABELS = labels(
            "PLANT_BREEDER_CERTIFICATE", "certificado de obtentor",
            "INDUSTRIAL_DESIGN", "diseño industrial",
            "INVENTION_PATENT", "patente de invención",
            "DISTINCTIVE_SIGN", "signo distintivo",
            "UTILITY_MODEL", "modelo de utilidad",
            "TRADE_SECRET", "secreto empresarial",
            "COPYRIGHT", "derecho de autor");
    private static final Map<String, String> NARRATIVE_APPLICABILITY_LABELS = labels(
            "LIKELY", "se relaciona claramente",
            "POSSIBLE", "podría corresponder",
            "UNLIKELY", "relación limitada");
    private static final Pattern NARRATIVE_PAIR = pairPattern();
    private static final Pattern NARRATIVE_PROTECTION = tokenPattern(NARRATIVE_PROTECTION_LABELS);
    private static final Pattern NARRATIVE_APPLICABILITY = tokenPattern(NARRATIVE_APPLICABILITY_LABELS);

    private DiagnosticReportPresentation() {
    }

    static String applicabilityLabel(String value) {
        return switch (value) {
            case "LIKELY" -> "Se relaciona claramente";
            case "POSSIBLE" -> "Podría corresponder";
            case "UNLIKELY" -> "La relación es limitada";
            default -> throw new IllegalArgumentException("Unsupported applicability: " + value);
        };
    }

    static String orientation(String value) {
        return switch (value) {
            case "LIKELY" -> "Esta modalidad presenta una relación clara con la materia descrita y puede considerarse como una vía principal para una revisión especializada.";
            case "POSSIBLE" -> "Esta modalidad podría corresponder a parte de la materia descrita. Se recomienda revisar la información disponible y considerar una evaluación especializada.";
            case "UNLIKELY" -> "Con la información disponible, la relación con esta modalidad es limitada.";
            default -> throw new IllegalArgumentException("Unsupported applicability: " + value);
        };
    }

    static String protectionLabel(String value) {
        return switch (value) {
            case "INVENTION_PATENT" -> "Patente de invención";
            case "UTILITY_MODEL" -> "Modelo de utilidad";
            case "INDUSTRIAL_DESIGN" -> "Diseño industrial";
            case "COPYRIGHT" -> "Derecho de autor";
            case "DISTINCTIVE_SIGN" -> "Signo distintivo";
            case "PLANT_BREEDER_CERTIFICATE" -> "Certificado de obtentor";
            case "TRADE_SECRET" -> "Secreto empresarial";
            case "OTHER" -> "Otra modalidad";
            default -> throw new IllegalArgumentException("Unsupported protection type: " + value);
        };
    }

    static String narrative(String value) {
        String presented = replacePairs(value);
        presented = replaceTokens(presented, NARRATIVE_PROTECTION, NARRATIVE_PROTECTION_LABELS);
        return replaceTokens(presented, NARRATIVE_APPLICABILITY, NARRATIVE_APPLICABILITY_LABELS);
    }

    static List<String> narratives(List<String> values) {
        return values.stream().map(DiagnosticReportPresentation::narrative).toList();
    }

    static DiagnosticReport.DisclaimerPresentation disclaimer(ReportDisclaimer value) {
        return new DiagnosticReport.DisclaimerPresentation(value.getDisclaimerId(), value.getVersion(),
                value.getTitle(), value.getContent());
    }

    static void requireSupportedContext(AnalysisReportContext context) {
        if (!AnalysisReportContextService.REPORT_VERSION.equals(context.getReportVersion())) {
            throw new IllegalArgumentException("Unsupported diagnostic report version");
        }
    }

    private static String replacePairs(String value) {
        Matcher matcher = NARRATIVE_PAIR.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = NARRATIVE_PROTECTION_LABELS.get(matcher.group(1))
                    + " — " + NARRATIVE_APPLICABILITY_LABELS.get(matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceTokens(String value, Pattern pattern, Map<String, String> labels) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(labels.get(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Pattern pairPattern() {
        return Pattern.compile("(?<![A-Z0-9_])(" + alternatives(NARRATIVE_PROTECTION_LABELS)
                + ")\\s*\\(\\s*(" + alternatives(NARRATIVE_APPLICABILITY_LABELS)
                + ")\\s*\\)(?![A-Z0-9_])");
    }

    private static Pattern tokenPattern(Map<String, String> labels) {
        return Pattern.compile("(?<![A-Z0-9_])(" + alternatives(labels) + ")(?![A-Z0-9_])");
    }

    private static String alternatives(Map<String, String> labels) {
        return labels.keySet().stream().map(Pattern::quote).reduce((left, right) -> left + "|" + right)
                .orElseThrow();
    }

    private static Map<String, String> labels(String... entries) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            labels.put(entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(labels);
    }
}
