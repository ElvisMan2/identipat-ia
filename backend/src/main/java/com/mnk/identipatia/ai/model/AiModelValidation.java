package com.mnk.identipatia.ai.model;

import java.util.regex.Pattern;

final class AiModelValidation {

    private static final Pattern RESOURCE_PART = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private AiModelValidation() {
    }

    static String resourcePart(String value, String field) {
        if (value == null || !RESOURCE_PART.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException(field + " must be a safe, non-empty resource identifier");
        }
        return value;
    }

    static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static String sha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hash");
        }
        return value;
    }
}
