package com.mnk.identipatia.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContactContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyPhoneIsIgnoredWithoutRelaxingOtherUnknownProperties() throws Exception {
        StandardUserRegistrationRequest registration = objectMapper.readValue(
                "{\"phone\":\"016543210\",\"mobilePhone\":\"912345678\"}",
                StandardUserRegistrationRequest.class);
        UserDTO user = objectMapper.readValue(
                "{\"phone\":\"016543210\",\"mobilePhone\":\"912345678\"}",
                UserDTO.class);

        assertThat(registration.getMobilePhone()).isEqualTo("912345678");
        assertThat(user.getMobilePhone()).isEqualTo("912345678");
        assertThat(objectMapper.writeValueAsString(registration)).doesNotContain("phone");
        assertThat(objectMapper.writeValueAsString(user)).doesNotContain("phone");
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"unexpected\":true}", StandardUserRegistrationRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void openApiSchemasExposeOnlyMobilePhone() {
        assertMobileOnly(schema(StandardUserRegistrationRequest.class));
        assertMobileOnly(schema(UserDTO.class));
    }

    private static Schema<?> schema(Class<?> type) {
        return ModelConverters.getInstance().read(type).get(type.getSimpleName());
    }

    private static void assertMobileOnly(Schema<?> schema) {
        assertThat(schema).isNotNull();
        assertThat(schema.getProperties())
                .containsKey("mobilePhone")
                .doesNotContainKey("phone");
    }
}
