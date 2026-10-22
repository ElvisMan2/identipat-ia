package com.mnk.identipatia.service;

import com.mnk.identipatia.exception.InvalidUserDataException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record UserSearchCriteria(
        int page,
        int size,
        List<String> searchTerms,
        String userType,
        String status,
        String doiType,
        String sortField,
        Sort.Direction sortDirection) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT = "creationDate,desc";

    private static final Set<String> SORT_FIELDS = Set.of(
            "userId",
            "firstName",
            "paternalLastName",
            "doi",
            "email",
            "userType",
            "status",
            "creationDate");
    private static final Set<String> USER_TYPES = Set.of("ADMIN", "STANDARD");
    private static final Set<String> STATUSES = Set.of("A", "I");
    private static final Set<String> DOI_TYPES = Set.of("DNI", "CE");

    public UserSearchCriteria {
        searchTerms = List.copyOf(searchTerms);
    }

    public static UserSearchCriteria of(
            int page,
            int size,
            String search,
            String userType,
            String status,
            String doiType,
            String sort) {
        if (page < 0) {
            throw new InvalidUserDataException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new InvalidUserDataException("size must be between 1 and 100");
        }

        String[] sortParts = normalizeSort(sort).split(",", -1);
        if (sortParts.length != 2) {
            throw new InvalidUserDataException("sort must have the format field,direction");
        }
        String sortField = sortParts[0].trim();
        String direction = sortParts[1].trim().toLowerCase(Locale.ROOT);
        if (!SORT_FIELDS.contains(sortField)) {
            throw new InvalidUserDataException("Unsupported sort field: " + sortField);
        }
        if (!Set.of("asc", "desc").contains(direction)) {
            throw new InvalidUserDataException("sort direction must be asc or desc");
        }

        return new UserSearchCriteria(
                page,
                size,
                normalizeSearchTerms(search),
                normalizeFilter("userType", userType, USER_TYPES),
                normalizeFilter("status", status, STATUSES),
                normalizeFilter("doiType", doiType, DOI_TYPES),
                sortField,
                Sort.Direction.fromString(direction));
    }

    public static UserSearchCriteria forExport(
            String search,
            String userType,
            String status,
            String doiType,
            String sort) {
        return of(DEFAULT_PAGE, DEFAULT_SIZE, search, userType, status, doiType, sort);
    }

    public Sort toSort() {
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(sortDirection, sortField));
        if (!"userId".equals(sortField)) {
            orders.add(Sort.Order.asc("userId"));
        }
        return Sort.by(orders);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, toSort());
    }

    private static String normalizeSort(String sort) {
        return sort == null ? DEFAULT_SORT : sort.trim();
    }

    private static List<String> normalizeSearchTerms(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return List.of(search.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    private static String normalizeFilter(String parameter, String value, Set<String> allowedValues) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new InvalidUserDataException("Invalid " + parameter + ": " + value.trim());
        }
        return normalized;
    }
}
