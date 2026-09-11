package com.mnk.identipatia.preprocessing.client;

public record PreprocessingHealthResponse(String status, String service, String version) {

    boolean isValid() {
        return "UP".equals(status)
                && "preprocessing-service".equals(service)
                && version != null
                && !version.isBlank();
    }
}
