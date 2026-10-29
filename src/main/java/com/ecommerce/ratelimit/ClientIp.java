package com.ecommerce.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The visitor's IP. Requests reach the backend through Vercel's /api rewrite and Render's proxy,
 * so the connection's own address is the proxy's; the client is the first X-Forwarded-For entry.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
