package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token CSRF para eco en X-XSRF-TOKEN; no autentica al usuario.")
public record CsrfTokenResponse(String token, String headerName) {

    @Override
    public String toString() {
        return "CsrfTokenResponse[token=<redacted>, headerName=" + headerName + "]";
    }
}
