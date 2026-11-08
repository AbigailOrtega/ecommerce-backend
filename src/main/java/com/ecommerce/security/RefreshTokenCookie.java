package com.ecommerce.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The refresh token travels only in an HttpOnly cookie, so page scripts (and an XSS) can never
 * read it. It is scoped to /api/auth: the only endpoints that need it are /refresh and /logout.
 * SameSite=Strict keeps other sites from making the browser send it.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /** Off only for local http development; browsers drop Secure cookies outside https (except Chrome on localhost). */
    @Value("${app.jwt.refresh-cookie-secure:true}")
    private boolean secure = true;

    public String create(String refreshToken) {
        return build(refreshToken, Duration.ofMillis(refreshTokenExpiration));
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
