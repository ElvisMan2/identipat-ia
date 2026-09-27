package com.mnk.identipatia.analysis.result.v2;

import com.mnk.identipatia.analysis.result.AnalysisResult;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AnalysisResultV2(
        String schemaVersion,
        String summary,
        List<ProtectionOption> protectionOptions,
        PatentScreening patentScreening,
        GeneticResourceAccess geneticResourceAccess,
        List<String> observations,
        List<String> warnings) implements AnalysisResult {

    public static final String SCHEMA_VERSION = "analysis-result/2.0";

    public AnalysisResultV2 {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported analysis result schemaVersion");
        }
        ResultValidation.requireText(summary, "summary");
        protectionOptions = ResultValidation.immutable(protectionOptions, "protectionOptions");
        if (new HashSet<>(protectionOptions.stream().map(ProtectionOption::type).toList()).size()
                != protectionOptions.size()) {
            throw new IllegalArgumentException("protectionOptions must not repeat type");
        }
        Objects.requireNonNull(patentScreening, "patentScreening must not be null");
        Objects.requireNonNull(geneticResourceAccess, "geneticResourceAccess must not be null");
        observations = ResultValidation.immutableText(observations, "observations");
        warnings = ResultValidation.immutableText(warnings, "warnings");
    }

    @Override
    public String toString() {
        return "AnalysisResultV2[schemaVersion=" + schemaVersion + ", content=<redacted>]";
    }
}
