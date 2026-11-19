package com.mnk.identipatia.analysis.result.v3;

import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.v2.GeneticResourceAccess;
import com.mnk.identipatia.analysis.result.v2.PatentScreening;
import com.mnk.identipatia.analysis.result.v2.ProtectionOption;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AnalysisResultV3(
        String schemaVersion,
        String summary,
        List<ProtectionOption> protectionOptions,
        PatentScreening patentScreening,
        GeneticResourceAccess geneticResourceAccess,
        TraditionalKnowledgeAccess traditionalKnowledgeAccess,
        List<String> observations,
        List<String> warnings) implements AnalysisResult {

    public static final String SCHEMA_VERSION = "analysis-result/3.0";

    public AnalysisResultV3 {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported analysis result schemaVersion");
        }
        requireText(summary, "summary");
        protectionOptions = immutable(protectionOptions, "protectionOptions");
        if (new HashSet<>(protectionOptions.stream().map(ProtectionOption::type).toList()).size()
                != protectionOptions.size()) {
            throw new IllegalArgumentException("protectionOptions must not repeat type");
        }
        Objects.requireNonNull(patentScreening, "patentScreening must not be null");
        Objects.requireNonNull(geneticResourceAccess, "geneticResourceAccess must not be null");
        Objects.requireNonNull(traditionalKnowledgeAccess, "traditionalKnowledgeAccess must not be null");
        observations = immutableText(observations, "observations");
        warnings = immutableText(warnings, "warnings");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
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
        copy.forEach(value -> requireText(value, field + " item"));
        return copy;
    }

    @Override
    public String toString() {
        return "AnalysisResultV3[schemaVersion=" + schemaVersion + ", content=<redacted>]";
    }
}
