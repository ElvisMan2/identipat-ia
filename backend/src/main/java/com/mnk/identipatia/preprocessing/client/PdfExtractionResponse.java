package com.mnk.identipatia.preprocessing.client;

public record PdfExtractionResponse(
        String text,
        int pageCount,
        int characterCount,
        String extractionMethod) {

    public boolean isValid() {
        return text != null && !text.isBlank()
                && pageCount > 0
                && characterCount == text.codePointCount(0, text.length())
                && "TEXT_LAYER".equals(extractionMethod);
    }
}
