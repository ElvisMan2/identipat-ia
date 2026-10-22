package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.UserDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserCsvExporterTest {

    private final UserCsvExporter exporter = new UserCsvExporter();

    @Test
    void exportsUtf8BomExactHeaderCrLfEscapedValuesAndNoPassword() {
        UserDTO user = UserDTO.builder()
                .userId(7L)
                .password("never-export-this")
                .firstName("Ana, \"Nani\"")
                .paternalLastName("Torres\nSalas")
                .maternalLastName("Vega")
                .doi("12345678")
                .doiType("DNI")
                .birthDate(LocalDate.of(1990, 1, 2))
                .gender("FEMALE")
                .email("ana@example.test")
                .phone("016543210")
                .mobilePhone("912345678")
                .userType("STANDARD")
                .profession("Abogada")
                .status("A")
                .build();

        byte[] result = exporter.export(List.of(user));
        String csv = new String(result, StandardCharsets.UTF_8);

        assertThat(result).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(csv).startsWith("\uFEFF\"ID\",\"Nombre completo\",\"Documento\",\"Nacimiento\",\"Género\",\"Correo\",\"Teléfono\",\"Celular\",\"Tipo\",\"Profesión\",\"Estado\"\r\n");
        assertThat(csv).contains("\"Ana, \"\"Nani\"\" Torres\nSalas Vega\"");
        assertThat(csv).contains("\"02/01/1990\"");
        assertThat(csv).doesNotContain("never-export-this");
    }

    @Test
    void prefixesFormulaCharactersIncludingAfterLeadingWhitespace() {
        UserDTO user = UserDTO.builder()
                .firstName("=SUM(A1:A2)")
                .gender("+cmd")
                .email("-malicious@example.test")
                .phone("@payload")
                .profession("  =hidden")
                .build();

        String csv = new String(exporter.export(List.of(user)), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"'=SUM(A1:A2)\"");
        assertThat(csv).contains("\"'+cmd\"");
        assertThat(csv).contains("\"'-malicious@example.test\"");
        assertThat(csv).contains("\"'@payload\"");
        assertThat(csv).contains("\"'  =hidden\"");
    }

    @Test
    void emptyResultContainsOnlyBomAndHeaderAndNullsBecomeEmptyCells() {
        String emptyCsv = new String(exporter.export(List.of()), StandardCharsets.UTF_8);
        String nullCsv = new String(exporter.export(List.of(UserDTO.builder().build())), StandardCharsets.UTF_8);

        assertThat(emptyCsv).isEqualTo("\uFEFF\"ID\",\"Nombre completo\",\"Documento\",\"Nacimiento\",\"Género\",\"Correo\",\"Teléfono\",\"Celular\",\"Tipo\",\"Profesión\",\"Estado\"");
        assertThat(nullCsv).endsWith("\r\n\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"");
    }
}
