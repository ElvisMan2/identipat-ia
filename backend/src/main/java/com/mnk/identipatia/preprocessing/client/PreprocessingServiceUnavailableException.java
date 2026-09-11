package com.mnk.identipatia.preprocessing.client;

public class PreprocessingServiceUnavailableException extends RuntimeException {

    public PreprocessingServiceUnavailableException(String message) {
        super(message);
    }

    public PreprocessingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
