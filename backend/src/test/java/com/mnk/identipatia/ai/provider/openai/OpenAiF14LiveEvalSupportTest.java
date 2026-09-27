package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OpenAiF14LiveEvalSupportTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesConfiguredMaxOutputTokensOrUsesDefault() {
        assertThat(OpenAiF14LiveEvalIT.parseMaxOutputTokens(null)).isEqualTo(6000);
        assertThat(OpenAiF14LiveEvalIT.parseMaxOutputTokens("  ")).isEqualTo(6000);
        assertThat(OpenAiF14LiveEvalIT.parseMaxOutputTokens(" 7200 ")).isEqualTo(7200);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenAiF14LiveEvalIT.parseMaxOutputTokens("0"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenAiF14LiveEvalIT.parseMaxOutputTokens("not-a-number"));
    }

    @Test
    void savesAFormattedStructuredResponseByCaseId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree("{\"schemaVersion\":\"2.0\",\"observations\":[]}");

        OpenAiF14LiveEvalIT.saveStructuredResponse(
                objectMapper, temporaryDirectory, "J01", response);

        Path output = temporaryDirectory.resolve("J01.json");
        assertThat(output).exists();
        assertThat(objectMapper.readTree(Files.readString(output))).isEqualTo(response);
        assertThatIllegalArgumentException().isThrownBy(() ->
                OpenAiF14LiveEvalIT.saveStructuredResponse(
                        objectMapper, temporaryDirectory, "../outside", response));
    }
}
