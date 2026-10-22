package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.UserDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class UserCsvExporter {

    private static final String BOM = "\uFEFF";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> HEADERS = List.of(
            "ID",
            "Nombre completo",
            "Documento",
            "Nacimiento",
            "Género",
            "Correo",
            "Teléfono",
            "Celular",
            "Tipo",
            "Profesión",
            "Estado");

    public byte[] export(List<UserDTO> users) {
        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, HEADERS);
        for (UserDTO user : users) {
            csv.append(LINE_SEPARATOR);
            appendRow(csv, List.of(
                    value(user.getUserId()),
                    fullName(user),
                    document(user),
                    user.getBirthDate() == null ? "" : BIRTH_DATE_FORMAT.format(user.getBirthDate()),
                    value(user.getGender()),
                    value(user.getEmail()),
                    value(user.getPhone()),
                    value(user.getMobilePhone()),
                    value(user.getUserType()),
                    value(user.getProfession()),
                    value(user.getStatus())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String fullName(UserDTO user) {
        return joinNonEmpty(
                value(user.getFirstName()),
                value(user.getPaternalLastName()),
                value(user.getMaternalLastName()));
    }

    private static String document(UserDTO user) {
        return joinNonEmpty(value(user.getDoiType()), value(user.getDoi()));
    }

    private static String joinNonEmpty(String... parts) {
        return Stream.of(parts)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
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
