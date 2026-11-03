package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record OperationalAnalysisListCriteria(
        int page,
        int size,
        List<String> searchTerms,
        LocalDate from,
        LocalDate to,
        AnalysisStatus status,
        OperationalAnalysisOrigin origin,
        AnalysisInputType inputType,
        String sortField,
        Sort.Direction sortDirection) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT = "createdAt,desc";

    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "status", "inputType", "analysisId");

    public OperationalAnalysisListCriteria {
        searchTerms = List.copyOf(searchTerms);
    }

    public static OperationalAnalysisListCriteria of(
            int page,
            int size,
            String search,
            LocalDate from,
            LocalDate to,
            String status,
            String origin,
            String inputType,
            String sort) {
        if (page < 0) {
            throw invalid("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw invalid("size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "from must not be after to");
        }

        String[] sortParts = (sort == null ? DEFAULT_SORT : sort.trim()).split(",", -1);
        if (sortParts.length != 2) {
            throw invalid("sort must have the format field,direction");
        }
        String sortField = sortParts[0].trim();
        String direction = sortParts[1].trim().toLowerCase(Locale.ROOT);
        if (!SORT_FIELDS.contains(sortField)) {
            throw invalid("Unsupported sort field: " + sortField);
        }
        if (!Set.of("asc", "desc").contains(direction)) {
            throw invalid("sort direction must be asc or desc");
        }

        return new OperationalAnalysisListCriteria(
                page,
                size,
                normalizeSearch(search),
                from,
                to,
                normalizeEnum("status", status, AnalysisStatus.class),
                normalizeEnum("origin", origin, OperationalAnalysisOrigin.class),
                normalizeEnum("inputType", inputType, AnalysisInputType.class),
                sortField,
                Sort.Direction.fromString(direction));
    }

    public Instant fromInclusive() {
        return new OperationalAnalysisReportCriteria(from, to).fromInclusive();
    }

    public Instant toExclusive() {
        return new OperationalAnalysisReportCriteria(from, to).toExclusive();
    }

    public Pageable toPageable() {
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(sortDirection, sortField));
        if (!"analysisId".equals(sortField)) {
            orders.add(new Sort.Order(sortDirection, "analysisId"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
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
