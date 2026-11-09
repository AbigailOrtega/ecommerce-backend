package com.ecommerce.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The visitor's IP: the first X-Forwarded-For entry. Confirmed in production (2026-11): requests go
 * browser → Vercel (/api rewrite) → Cloudflare at Render → app, and arrive with
 * X-Forwarded-For "client, vercel-egress"; CF-Connecting-IP and the connection address are Vercel's
 * and Cloudflare's, shared by every visitor. Vercel overwrites the header, so it cannot be forged
 * through the store; a caller hitting Render directly can forge it, which only per-IP limits rely on
 * (the per-email limits do not).
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
