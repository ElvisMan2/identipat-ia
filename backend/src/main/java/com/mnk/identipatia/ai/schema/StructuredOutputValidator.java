package com.mnk.identipatia.ai.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.Objects;
import java.util.Set;

public class StructuredOutputValidator {

    private final JsonSchemaFactory schemaFactory;

    public StructuredOutputValidator() {
        this(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012));
    }

    StructuredOutputValidator(JsonSchemaFactory schemaFactory) {
        this.schemaFactory = Objects.requireNonNull(schemaFactory, "schemaFactory must not be null");
    }

    public void validate(StructuredOutputDefinition definition, JsonNode output, String provider) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(output, "output must not be null");
        try {
            JsonSchema schema = schemaFactory.getSchema(definition.jsonSchema());
            Set<ValidationMessage> violations = schema.validate(output);
            if (!violations.isEmpty()) {
                throw invalidResponse(provider, null);
            }
        } catch (GenerativeAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse(provider, exception);
        }
    }

    private static GenerativeAiException invalidResponse(String provider, Throwable cause) {
        return new GenerativeAiException(
                GenerativeAiErrorType.INVALID_RESPONSE,
                provider,
                false,
                "The provider returned output that does not satisfy the required schema",
                cause);
    }
}
