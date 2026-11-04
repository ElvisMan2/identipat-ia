package com.mnk.identipatia.reporting.dto;

import com.mnk.identipatia.exception.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record OperationalAnalysisSort(String field, Sort.Direction direction) {
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "status", "inputType", "analysisId");

    public static OperationalAnalysisSort of(String value, String defaultValue) {
        String[] parts = (value == null ? defaultValue : value.trim()).split(",", -1);
        if (parts.length != 2) {
            throw invalid("sort must have the format field,direction");
        }
        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!SORT_FIELDS.contains(field)) {
            throw invalid("Unsupported sort field: " + field);
        }
        if (!Set.of("asc", "desc").contains(direction)) {
            throw invalid("sort direction must be asc or desc");
        }
        return new OperationalAnalysisSort(field, Sort.Direction.fromString(direction));
    }

    public Sort toSort() {
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(direction, field));
        if (!"analysisId".equals(field)) {
            orders.add(new Sort.Order(direction, "analysisId"));
        }
        return Sort.by(orders);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANALYSIS_QUERY", message);
    }
}
