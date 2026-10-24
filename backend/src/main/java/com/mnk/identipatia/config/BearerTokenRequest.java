package com.mnk.identipatia.config;

import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokenRequest {
    private static final String PREFIX = "Bearer ";

    private BearerTokenRequest() {
    }

    public static boolean isPresent(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null
                && authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }
}
