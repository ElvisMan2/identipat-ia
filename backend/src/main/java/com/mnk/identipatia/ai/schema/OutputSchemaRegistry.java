package com.mnk.identipatia.ai.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class OutputSchemaRegistry {

    private static final String ROOT = "ai-schemas/";
    private final ObjectMapper objectMapper;

    public OutputSchemaRegistry(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public StructuredOutputDefinition load(String schemaId, String schemaVersion) {
        StructuredOutputDefinition identifierCheck = new StructuredOutputDefinition(
                schemaId, schemaVersion, objectMapper.createObjectNode(), true);
        String path = ROOT + identifierCheck.schemaId() + "/v" + identifierCheck.schemaVersion() + "/schema.json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Output schema not found: " + schemaId
                    + " version " + schemaVersion);
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            JsonNode schema = objectMapper.readTree(content);
            return new StructuredOutputDefinition(schemaId, schemaVersion, schema, true);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Output schema is not valid JSON: " + schemaId
                    + " version " + schemaVersion, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Output schema could not be read: " + schemaId
                    + " version " + schemaVersion, exception);
        }
    }
}
