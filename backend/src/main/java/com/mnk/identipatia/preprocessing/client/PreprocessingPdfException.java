package com.mnk.identipatia.preprocessing.client;

public class PreprocessingPdfException extends RuntimeException {
    private final String code;

    public PreprocessingPdfException(String code) {
        super("Preprocessing service rejected the PDF with code " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
