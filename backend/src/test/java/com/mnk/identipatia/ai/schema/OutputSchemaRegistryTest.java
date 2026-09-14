package com.mnk.identipatia.ai.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputSchemaRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutputSchemaRegistry registry = new OutputSchemaRegistry(objectMapper);
    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    @Test
    void loadsVersionedStrictSchemaFromClasspath() {
        StructuredOutputDefinition definition = registry.load("provider-smoke-result", "1.0");

        assertThat(definition.strict()).isTrue();
        assertThat(definition.jsonSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.jsonSchema().path("required")).hasSize(3);
    }

    @Test
    void rejectsMissingUnsafeAndInvalidJsonSchemas() {
        assertThatThrownBy(() -> registry.load("missing", "1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> registry.load("../secret", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.load("invalid-schema", "1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void acceptsValidOutput() throws Exception {
        validator.validate(definition(), objectMapper.readTree("""
                {"schemaVersion":"provider-smoke-result/1.0","status":"OK","message":"ready"}
                """), "test-provider");
    }

    @Test
    void rejectsMissingFieldInvalidEnumAdditionalPropertyAndWrongType() {
        assertInvalid("{\"schemaVersion\":\"provider-smoke-result/1.0\",\"status\":\"OK\"}");
        assertInvalid("{\"schemaVersion\":\"provider-smoke-result/1.0\",\"status\":\"FAIL\",\"message\":\"x\"}");
        assertInvalid("{\"schemaVersion\":\"provider-smoke-result/1.0\",\"status\":\"OK\",\"message\":\"x\",\"extra\":true}");
        assertInvalid("{\"schemaVersion\":\"provider-smoke-result/1.0\",\"status\":\"OK\",\"message\":1}");
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> validator.validate(definition(), objectMapper.readTree(json), "test-provider"))
                .isInstanceOfSatisfying(GenerativeAiException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.INVALID_RESPONSE);
                    assertThat(exception.provider()).isEqualTo("test-provider");
                    assertThat(exception.getMessage()).doesNotContain(json);
                });
    }

    private StructuredOutputDefinition definition() {
        return registry.load("provider-smoke-result", "1.0");
    }
}
