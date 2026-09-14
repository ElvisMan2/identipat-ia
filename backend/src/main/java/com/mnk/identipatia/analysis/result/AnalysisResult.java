package com.mnk.identipatia.analysis.result;

import java.util.List;
import java.util.Objects;

public record AnalysisResult(
        String schemaVersion,
        String summary,
        PatentabilityAssessment patentabilityAssessment,
        List<ProtectionOption> protectionOptions,
        List<String> observations,
        List<String> warnings) {

    public static final String SCHEMA_VERSION = "analysis-result/1.0";

    public AnalysisResult {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported analysis result schemaVersion");
        }
        PatentabilityAssessment.requireText(summary, "summary");
        Objects.requireNonNull(patentabilityAssessment, "patentabilityAssessment must not be null");
        protectionOptions = immutable(protectionOptions, "protectionOptions");
        observations = immutableText(observations, "observations");
        warnings = immutableText(warnings, "warnings");
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null values");
        }
        return List.copyOf(values);
    }

    private static List<String> immutableText(List<String> values, String field) {
        List<String> copy = immutable(values, field);
        copy.forEach(value -> PatentabilityAssessment.requireText(value, field + " item"));
        return copy;
    }

    @Override
    public String toString() {
        return "AnalysisResult[schemaVersion=" + schemaVersion + ", content=<redacted>]";
    }
}
