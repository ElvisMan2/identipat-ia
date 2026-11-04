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
import java.util.List;

public record OperationalAnalysisListCriteria(
        int page,
        int size,
        OperationalAnalysisFilters filters,
        OperationalAnalysisSort ordering) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT = "createdAt,desc";

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
        return new OperationalAnalysisListCriteria(
                page,
                size,
                OperationalAnalysisFilters.of(search, from, to, status, origin, inputType),
                OperationalAnalysisSort.of(sort, DEFAULT_SORT));
    }

    public Instant fromInclusive() {
        return filters.fromInclusive();
    }

    public Instant toExclusive() {
        return filters.toExclusive();
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, ordering.toSort());
    }

    public List<String> searchTerms() {
        return filters.searchTerms();
    }

    public AnalysisStatus status() {
        return filters.status();
    }

    public OperationalAnalysisOrigin origin() {
        return filters.origin();
    }

    public AnalysisInputType inputType() {
        return filters.inputType();
    }

    public String sortField() {
        return ordering.field();
    }

    public Sort.Direction sortDirection() {
        return ordering.direction();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANALYSIS_QUERY", message);
    }
}
