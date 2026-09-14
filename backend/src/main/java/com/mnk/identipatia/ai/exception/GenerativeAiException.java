package com.mnk.identipatia.ai.exception;

import java.util.Objects;

public class GenerativeAiException extends RuntimeException {

    private final GenerativeAiErrorType type;
    private final String provider;
    private final boolean retryable;

    public GenerativeAiException(
            GenerativeAiErrorType type,
            String provider,
            boolean retryable,
            String sanitizedMessage,
            Throwable cause) {
        super(requireMessage(sanitizedMessage), cause);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.provider = requireProvider(provider);
        this.retryable = retryable;
    }

    public GenerativeAiErrorType type() {
        return type;
    }

    public String provider() {
        return provider;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String requireProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        return provider;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("sanitizedMessage must not be blank");
        }
        return message;
    }
}
