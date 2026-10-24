package com.mnk.identipatia.preprocessing.client;

public class PreprocessingAudioException extends RuntimeException {
    private final String code;

    public PreprocessingAudioException(String code) {
        super("Preprocessing service rejected the audio with code " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
