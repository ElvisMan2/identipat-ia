package com.mnk.identipatia.service;

import com.mnk.identipatia.config.StandardSessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@Service
public class StandardSessionCookieService {
    public static final String COOKIE_PATH = "/identipat-ia";

    private final StandardSessionProperties properties;

    public StandardSessionCookieService(StandardSessionProperties properties) {
        this.properties = properties;
    }

    public String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public void write(HttpServletResponse response, String token, Instant absoluteExpiresAt, Instant now) {
        long seconds = Math.max(0, Duration.between(now, absoluteExpiresAt).getSeconds());
        add(response, ResponseCookie.from(properties.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(seconds)
                .build());
    }

    public void clear(HttpServletResponse response) {
        add(response, ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build());
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
