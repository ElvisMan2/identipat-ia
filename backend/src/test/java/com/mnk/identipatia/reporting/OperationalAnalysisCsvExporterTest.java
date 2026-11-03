package com.mnk.identipatia.reporting;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportRow;
import com.mnk.identipatia.reporting.dto.OperationalModalityApplicability;
import com.mnk.identipatia.reporting.service.OperationalAnalysisCsvExporter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAnalysisCsvExporterTest {
    private static final List<String> EXPECTED_HEADERS = List.of(
            "Id Analisis", "Conclusion", "Estado del Analisis", "Reporte",
            "Reporte PDF Disponible", "Nombres", "Apellidos", "Tipo de Documento",
            "Numero de Documento", "Genero", "Fecha de Nacimiento", "Telefono", "Celular",
            "Correo", "Profesion u Oficio", "Fecha de Creacion", "Tipo de Entrada",
            "Modalidades Identificadas", "Aplicabilidad por Modalidad", "Origen del Analisis",
            "Orientacion Consultada");

    private final OperationalAnalysisCsvExporter exporter = new OperationalAnalysisCsvExporter();

    @Test
    void writesExactHeaderUtf8BomCrlfAccentsAndAnEmptyDataset() {
        byte[] empty = exporter.export(List.of());
        String emptyCsv = new String(empty, StandardCharsets.UTF_8);

        assertThat(empty).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(parse(emptyCsv)).containsExactly(EXPECTED_HEADERS);
        assertThat(emptyCsv).doesNotContain("\r\n");

        String csv = text(exporter.export(List.of(row(UUID.randomUUID(), AnalysisStatus.COMPLETED,
                AnalysisInputType.TEXT, "Femenina con áéíóú ñ Ñ", "Reporte", "FEMALE"))));
        assertThat(csv).contains("áéíóú ñ Ñ");
        assertThat(csv).contains("\r\n");
        assertThat(csv.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void quotesEscapesAndKeepsMultilineReportInOneCell() {
        OperationalAnalysisReportRow source = row(UUID.randomUUID(), AnalysisStatus.COMPLETED,
                AnalysisInputType.TEXT, "Conclusión, \"citada\"", "Línea 1, \"citada\"\nLínea 2\r\nLínea 3", "OTHER");

        String csv = text(exporter.export(List.of(source)));
        List<List<String>> parsed = parse(csv);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(1)).hasSize(21);
        assertThat(parsed.get(1).get(1)).isEqualTo("Conclusión, \"citada\"");
        assertThat(parsed.get(1).get(3)).isEqualTo("Línea 1, \"citada\"\nLínea 2\r\nLínea 3");
        assertThat(csv).contains("\"Conclusión, \"\"citada\"\"\"");
    }

    @Test
    void nullsBecomeEmptyAndTextualFormulaPayloadsAreNeutralized() {
        OperationalAnalysisReportRow source = new OperationalAnalysisReportRow(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "=conclusion", AnalysisStatus.RECEIVED, "+report", false,
                "-name", "@last", "  =type", "08711340", null, null,
                "043426080", "091234567", null, "  =profession", null,
                AnalysisInputType.TEXT, List.of("+modalidad"),
                List.of(new OperationalModalityApplicability("@modalidad", "-aplicabilidad")),
                OperationalAnalysisOrigin.STANDARD, false);

        List<String> values = parse(text(exporter.export(List.of(source)))).get(1);

        assertThat(values).contains("'=conclusion", "'+report", "'-name", "'@last", "'  =type",
                "'  =profession", "'+modalidad", "'@modalidad: -aplicabilidad");
        assertThat(values.get(8)).isEqualTo("08711340");
        assertThat(values.get(11)).isEqualTo("043426080");
        assertThat(values.get(12)).isEqualTo("091234567");
        assertThat(values.get(9)).isEmpty();
        assertThat(values.get(10)).isEmpty();
        assertThat(values.get(13)).isEmpty();
        assertThat(values.get(15)).isEmpty();
    }

    @Test
    void mapsStatusesInputTypesGendersBooleansDatesModalitiesApplicabilityAndOrigins() {
        List<OperationalAnalysisReportRow> rows = List.of(
                mappedRow(1, AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, "MALE", true, false,
                        OperationalAnalysisOrigin.STANDARD),
                mappedRow(2, AnalysisStatus.PREPROCESSING, AnalysisInputType.PDF, "FEMALE", false, true,
                        OperationalAnalysisOrigin.ADMIN),
                mappedRow(3, AnalysisStatus.ANALYZING, AnalysisInputType.AUDIO, "OTHER", true, true,
                        OperationalAnalysisOrigin.STANDARD),
                mappedRow(4, AnalysisStatus.COMPLETED, AnalysisInputType.TEXT, "HISTORICO", false, false,
                        OperationalAnalysisOrigin.ADMIN),
                mappedRow(5, AnalysisStatus.FAILED, AnalysisInputType.PDF, null, false, false,
                        OperationalAnalysisOrigin.ADMIN));

        List<List<String>> values = parse(text(exporter.export(rows))).subList(1, 6);

        assertThat(values).extracting(row -> row.get(2))
                .containsExactly("RECIBIDO", "PREPROCESANDO", "ANALIZANDO", "COMPLETADO", "FALLIDO");
        assertThat(values).extracting(row -> row.get(16))
                .containsExactly("Texto", "PDF", "Audio", "Texto", "PDF");
        assertThat(values).extracting(row -> row.get(9))
                .containsExactly("Masculino", "Femenino", "Otro", "HISTORICO", "");
        assertThat(values.get(0).get(4)).isEqualTo("SI");
        assertThat(values.get(0).get(20)).isEqualTo("NO");
        assertThat(values.get(1).get(4)).isEqualTo("NO");
        assertThat(values.get(1).get(20)).isEqualTo("SI");
        assertThat(values.get(0).get(10)).isEqualTo("1990-01-02");
        assertThat(values.get(0).get(15)).isEqualTo("2026-11-03 13:45:17");
        assertThat(values.get(0).get(17)).isEqualTo("Patente de invención | Modelo de utilidad");
        assertThat(values.get(0).get(18)).isEqualTo(
                "Patente de invención: Se relaciona claramente | Modelo de utilidad: Podría corresponder");
        assertThat(values).extracting(row -> row.get(19))
                .containsExactly("STANDARD", "ADMIN", "STANDARD", "ADMIN", "ADMIN");
    }

    @Test
    void preservesInputRowOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000002");

        List<List<String>> rows = parse(text(exporter.export(List.of(
                row(first, AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, null, null, null),
                row(second, AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, null, null, null),
                row(third, AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, null, null, null)))));

        assertThat(rows.subList(1, 4)).extracting(row -> row.get(0))
                .containsExactly(first.toString(), second.toString(), third.toString());
    }

    private static OperationalAnalysisReportRow mappedRow(
            int id, AnalysisStatus status, AnalysisInputType inputType, String gender,
            boolean pdf, boolean guidance, OperationalAnalysisOrigin origin) {
        return new OperationalAnalysisReportRow(
                new UUID(0, id), "Conclusión", status, "Reporte", pdf, "Ana", "Pérez", "DNI",
                "08711340", gender, LocalDate.of(1990, 1, 2), "043426080", "091234567",
                "ana@example.test", "Ingeniera", Instant.parse("2026-11-03T18:45:17Z"), inputType,
                List.of("Patente de invención", "Modelo de utilidad"),
                List.of(
                        new OperationalModalityApplicability(
                                "Patente de invención", "Se relaciona claramente"),
                        new OperationalModalityApplicability(
                                "Modelo de utilidad", "Podría corresponder")),
                origin, guidance);
    }

    private static OperationalAnalysisReportRow row(
            UUID id, AnalysisStatus status, AnalysisInputType inputType,
            String conclusion, String report, String gender) {
        return new OperationalAnalysisReportRow(
                id, conclusion, status, report, true, "Ana", "Pérez", "DNI", "08711340", gender,
                LocalDate.of(1990, 1, 2), "043426080", "091234567", "ana@example.test",
                "Profesión", Instant.parse("2026-11-03T18:45:17Z"), inputType, List.of(), List.of(),
                OperationalAnalysisOrigin.ADMIN, true);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<List<String>> parse(String csv) {
        int index = csv.startsWith("\uFEFF") ? 1 : 0;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        while (index < csv.length()) {
            char current = csv.charAt(index++);
            if (quoted) {
                if (current == '"') {
                    if (index < csv.length() && csv.charAt(index) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(current);
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\r' && index < csv.length() && csv.charAt(index) == '\n') {
                index++;
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        row.add(cell.toString());
        rows.add(row);
        return rows;
    }
}
