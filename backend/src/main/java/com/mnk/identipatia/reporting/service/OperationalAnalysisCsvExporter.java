package com.mnk.identipatia.reporting.service;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportRow;
import com.mnk.identipatia.reporting.dto.OperationalModalityApplicability;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OperationalAnalysisCsvExporter {
    private static final String BOM = "\uFEFF";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final String ITEM_SEPARATOR = " | ";
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(REPORT_ZONE);
    private static final List<String> HEADERS = List.of(
            "Id Analisis",
            "Conclusion",
            "Estado del Analisis",
            "Reporte",
            "Reporte PDF Disponible",
            "Nombres",
            "Apellidos",
            "Tipo de Documento",
            "Numero de Documento",
            "Genero",
            "Fecha de Nacimiento",
            "Celular",
            "Correo",
            "Profesion u Oficio",
            "Fecha de Creacion",
            "Tipo de Entrada",
            "Modalidades Identificadas",
            "Aplicabilidad por Modalidad",
            "Origen del Analisis",
            "Orientacion Consultada");

    public byte[] export(List<OperationalAnalysisReportRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, HEADERS);
        for (OperationalAnalysisReportRow row : rows) {
            csv.append(LINE_SEPARATOR);
            appendRow(csv, values(Objects.requireNonNull(row, "row must not be null")));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> values(OperationalAnalysisReportRow row) {
        return List.of(
                value(row.analysisId()),
                value(row.conclusion()),
                status(row.analysisStatus()),
                value(row.reportText()),
                yesNo(row.reportPdfAvailable()),
                value(row.firstName()),
                value(row.lastNames()),
                value(row.doiType()),
                value(row.doi()),
                gender(row.gender()),
                row.birthDate() == null ? "" : BIRTH_DATE_FORMAT.format(row.birthDate()),
                value(row.mobilePhone()),
                value(row.email()),
                value(row.profession()),
                row.createdAt() == null ? "" : CREATED_AT_FORMAT.format(row.createdAt()),
                inputType(row.inputType()),
                String.join(ITEM_SEPARATOR, row.identifiedModalities()),
                applicability(row.applicabilityByModality()),
                value(row.analysisOrigin()),
                yesNo(row.guidanceConsulted()));
    }

    private static String status(AnalysisStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case RECEIVED -> "RECIBIDO";
            case PREPROCESSING -> "PREPROCESANDO";
            case ANALYZING -> "ANALIZANDO";
            case COMPLETED -> "COMPLETADO";
            case FAILED -> "FALLIDO";
        };
    }

    private static String inputType(AnalysisInputType inputType) {
        if (inputType == null) {
            return "";
        }
        return switch (inputType) {
            case TEXT -> "Texto";
            case PDF -> "PDF";
            case AUDIO -> "Audio";
        };
    }

    private static String gender(String gender) {
        if (gender == null) {
            return "";
        }
        return switch (gender.toUpperCase(Locale.ROOT)) {
            case "MALE" -> "Masculino";
            case "FEMALE" -> "Femenino";
            case "OTHER" -> "Otro";
            default -> gender;
        };
    }

    private static String applicability(List<OperationalModalityApplicability> values) {
        return values.stream()
                .map(value -> value.modality() + ": " + value.applicability())
                .collect(Collectors.joining(ITEM_SEPARATOR));
    }

    private static String yesNo(boolean value) {
        return value ? "SI" : "NO";
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values.get(index)));
        }
    }

    private static String escape(String value) {
        String safeValue = preventCsvInjection(value);
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private static String preventCsvInjection(String value) {
        int firstSignificant = 0;
        while (firstSignificant < value.length()
                && Character.isWhitespace(value.charAt(firstSignificant))) {
            firstSignificant++;
        }
        if (firstSignificant < value.length()
                && "=+-@".indexOf(value.charAt(firstSignificant)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
