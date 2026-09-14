package com.mnk.identipatia.ai.model;

public record GenerationOptions(Integer maxOutputTokens, Double temperature) {

    public GenerationOptions {
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be greater than zero");
        }
        if (temperature != null && (!Double.isFinite(temperature) || temperature < 0)) {
            throw new IllegalArgumentException("temperature must be finite and non-negative");
        }
    }

    public static GenerationOptions defaults() {
        return new GenerationOptions(null, null);
    }
}
