package com.mnk.identipatia.preprocessing.client;

public record PdfInspectionResponse(
        int pageCount,
        boolean hasTextLayer,
        int characterCount,
        String extractionMethod) {

    public boolean isValid() {
        return pageCount > 0
                && characterCount >= 0
                && ((hasTextLayer && characterCount > 0 && "TEXT_LAYER".equals(extractionMethod))
                    || (!hasTextLayer && characterCount == 0 && "NONE".equals(extractionMethod)));
    }
}
