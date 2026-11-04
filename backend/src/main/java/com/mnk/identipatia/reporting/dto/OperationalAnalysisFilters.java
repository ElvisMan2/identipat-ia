package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public record OperationalAnalysisFilters(
        List<String> searchTerms,
        LocalDate from,
        LocalDate to,
        AnalysisStatus status,
        OperationalAnalysisOrigin origin,
        AnalysisInputType inputType) {
    public OperationalAnalysisFilters {
        searchTerms = List.copyOf(searchTerms);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "from must not be after to");
        }
    }

    public static OperationalAnalysisFilters of(
            String search,
            LocalDate from,
            LocalDate to,
            String status,
            String origin,
            String inputType) {
        return new OperationalAnalysisFilters(
                normalizeSearch(search),
                from,
                to,
                normalizeEnum("status", status, AnalysisStatus.class),
                normalizeEnum("origin", origin, OperationalAnalysisOrigin.class),
                normalizeEnum("inputType", inputType, AnalysisInputType.class));
    }

    public Instant fromInclusive() {
        return new OperationalAnalysisReportCriteria(from, to).fromInclusive();
    }

    public Instant toExclusive() {
        return new OperationalAnalysisReportCriteria(from, to).toExclusive();
    }

    public boolean hasAdditionalFilters() {
        return !searchTerms.isEmpty() || status != null || origin != null || inputType != null;
    }

    private static List<String> normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return List.of(search.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    private static <E extends Enum<E>> E normalizeEnum(
            String parameter, String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid " + parameter + ": " + value.trim());
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANALYSIS_QUERY", message);
    }
}
