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

    /** Every header that may carry the visitor's IP, to find the right one behind the proxies. */
    public static String describe(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder("remoteAddr=").append(request.getRemoteAddr());
        for (String h : new String[]{"X-Forwarded-For", "X-Real-IP", "X-Vercel-Forwarded-For",
                "X-Vercel-IP", "True-Client-IP", "CF-Connecting-IP", "Forwarded"}) {
            String v = request.getHeader(h);
            if (v != null) sb.append(' ').append(h).append('=').append(v);
        }
        return sb.toString();
    }
}
