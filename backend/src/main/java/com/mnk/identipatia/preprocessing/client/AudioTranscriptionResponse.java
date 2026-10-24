package com.mnk.identipatia.preprocessing.client;

public record AudioTranscriptionResponse(
        String text,
        double durationSeconds,
        int characterCount,
        String language,
        String transcriptionMethod,
        String model) {

    public boolean isValid() {
        return text != null && !text.isBlank()
                && Double.isFinite(durationSeconds) && durationSeconds > 0
                && characterCount == text.codePointCount(0, text.length())
                && "es".equals(language)
                && "FASTER_WHISPER".equals(transcriptionMethod)
                && model != null && !model.isBlank();
    }
}
