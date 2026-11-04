package com.mnk.identipatia.reporting.dto;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;

public record OperationalAnalysisExportCriteria(
        OperationalAnalysisFilters filters,
        OperationalAnalysisSort ordering) {
    public static final String DEFAULT_SORT = "createdAt,asc";

    public static OperationalAnalysisExportCriteria of(
            String search,
            LocalDate from,
            LocalDate to,
            String status,
            String origin,
            String inputType,
            String sort) {
        return new OperationalAnalysisExportCriteria(
                OperationalAnalysisFilters.of(search, from, to, status, origin, inputType),
                OperationalAnalysisSort.of(sort, DEFAULT_SORT));
    }

    public Sort toSort() {
        return ordering.toSort();
    }

    public boolean hasAdditionalFilters() {
        return filters.hasAdditionalFilters();
    }

    public LocalDate from() {
        return filters.from();
    }

    public LocalDate to() {
        return filters.to();
    }
}
