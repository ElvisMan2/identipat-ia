package com.mnk.identipatia.ai.provider.gemini;

@FunctionalInterface
interface GeminiResponseDiagnostics {

    void record(GeminiSafeResponseMetadata metadata);

    static GeminiResponseDiagnostics disabled() {
        return metadata -> {
        };
    }
}
