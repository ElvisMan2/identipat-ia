package com.mnk.identipatia.analysis.result.v2;

import java.util.List;
import java.util.Objects;

final class ResultValidation {
    private ResultValidation() {
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    static <T> List<T> immutable(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null values");
        }
        return List.copyOf(values);
    }

    static List<String> immutableText(List<String> values, String field) {
        List<String> copy = immutable(values, field);
        copy.forEach(value -> requireText(value, field + " item"));
        return copy;
    }
}
