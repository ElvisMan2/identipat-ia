package com.mnk.identipatia.exception;

import org.springframework.http.HttpStatus;

public class StandardSessionRequiredException extends ApiException {
    public StandardSessionRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "STANDARD_SESSION_REQUIRED", "A valid STANDARD session is required");
    }
}
