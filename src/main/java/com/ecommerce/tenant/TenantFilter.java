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

    public TenantFilter(TenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String host = request.getHeader(STORE_HOST);
        if (host == null || host.isBlank()) host = request.getHeader(FORWARDED_HOST);
        if (host == null || host.isBlank()) host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) host = request.getServerName();

        TenantResolver.Resolution resolution = resolver.resolve(host);
        if (!resolution.ok()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"" + resolution.error() + "\"}");
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
}
