package com.mnk.identipatia.analysis.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;

import java.util.Objects;

/** Resolves persisted results without reinterpreting one schema major as another. */
public final class AnalysisResultReader {
    private final ObjectMapper objectMapper;

    public AnalysisResultReader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AnalysisResult read(String storedSchemaVersion, JsonNode json) throws JsonProcessingException {
        Objects.requireNonNull(storedSchemaVersion, "storedSchemaVersion must not be null");
        Objects.requireNonNull(json, "json must not be null");
        String payloadSchemaVersion = json.path("schemaVersion").asText(null);
        if (!storedSchemaVersion.equals(payloadSchemaVersion)) {
            throw new IllegalArgumentException("Stored analysis result schemaVersion mismatch");
        }
        return switch (storedSchemaVersion) {
            case AnalysisResultV1.SCHEMA_VERSION -> objectMapper.treeToValue(json, AnalysisResultV1.class);
            case AnalysisResultV2.SCHEMA_VERSION -> objectMapper.treeToValue(json, AnalysisResultV2.class);
            default -> throw new IllegalArgumentException("Unsupported analysis result schemaVersion");
        };
    }

    public AnalysisResultV2 readCurrent(JsonNode json) throws JsonProcessingException {
        return (AnalysisResultV2) read(AnalysisResultV2.SCHEMA_VERSION, json);
    }
}
