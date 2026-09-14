package com.mnk.identipatia.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record StructuredOutputDefinition(
        String schemaId,
        String schemaVersion,
        JsonNode jsonSchema,
        boolean strict) {

    public StructuredOutputDefinition {
        AiModelValidation.resourcePart(schemaId, "schemaId");
        AiModelValidation.resourcePart(schemaVersion, "schemaVersion");
        Objects.requireNonNull(jsonSchema, "jsonSchema must not be null");
        if (!jsonSchema.isObject()) {
            throw new IllegalArgumentException("jsonSchema must be a JSON object");
        }
        jsonSchema = jsonSchema.deepCopy();
    }

    @Override
    public JsonNode jsonSchema() {
        return jsonSchema.deepCopy();
    }
}
