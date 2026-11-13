package com.ecommerce.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Picks the store for each request from the host the visitor used. Behind Vercel the request
 * reaches Render addressed to Render, so the frontend sends the visitor's host in X-Store-Host on
 * every API call (browser and server-side render); X-Forwarded-Host and Host are the fallbacks.
 *
 * <p>Trusting these headers is safe: choosing a store only selects which public catalog is shown,
 * the same as visiting that store's domain. Anything private is guarded by the login, and a
 * session token only works on the store that issued it (JwtAuthFilter).
 *
 * <p>Registered ahead of Spring Security in TenancyConfig, so authentication already sees the store.
 */
public class TenantFilter extends OncePerRequestFilter {

    public static final String STORE_HOST = "X-Store-Host";
    public static final String FORWARDED_HOST = "X-Forwarded-Host";

    private final TenantResolver resolver;
    /** The platform's own domain: no store answers there, only the platform's API. */
    private final String platformHost;

    public TenantFilter(TenantResolver resolver) {
        this(resolver, "");
    }

    public TenantFilter(TenantResolver resolver, String platformHost) {
        this.resolver = resolver;
        this.platformHost = platformHost == null ? "" : platformHost.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String host = requestHost(request);
        if (!platformHost.isEmpty() && platformHost.equals(host) && !request.getRequestURI().startsWith("/api/platform/")) {
            // the platform's domain is no store (not even #1): a store's API does not exist there
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"No hay tienda en este dominio\"}");
            return;
        }
        TenantResolver.Resolution resolution = resolver.resolve(host);
        if (!resolution.ok()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"" + resolution.code()
                    + "\",\"message\":\"" + resolution.error() + "\"}");
            return;
        }

        // Caches in front (Vercel) must keep one copy per store
        response.addHeader(HttpHeaders.VARY, STORE_HOST + ", " + FORWARDED_HOST);
        TenantContext.set(resolution.tenantId());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** The visitor's domain, normalized ("www.example.com"): X-Store-Host, then X-Forwarded-Host, then Host. */
    public static String requestHost(HttpServletRequest request) {
        String host = request.getHeader(STORE_HOST);
        if (host == null || host.isBlank()) host = request.getHeader(FORWARDED_HOST);
        if (host == null || host.isBlank()) host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) host = request.getServerName();
        return TenantResolver.normalize(host);
    }
}
