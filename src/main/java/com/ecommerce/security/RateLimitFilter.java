package com.ecommerce.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // ip -> bucket
    private final ConcurrentHashMap<String, Bucket> loginBuckets     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registerBuckets  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> guestOrderBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> pageviewBuckets  = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method)) {
            String ip = getClientIp(request);
            Bucket bucket = null;

            if (path.equals("/api/auth/login")) {
                bucket = loginBuckets.computeIfAbsent(ip, k -> newBucket(5, Duration.ofMinutes(1)));
            } else if (path.equals("/api/auth/register")) {
                bucket = registerBuckets.computeIfAbsent(ip, k -> newBucket(3, Duration.ofHours(1)));
            } else if (path.equals("/api/orders/guest")) {
                bucket = guestOrderBuckets.computeIfAbsent(ip, k -> newBucket(5, Duration.ofHours(1)));
            } else if (path.equals("/api/analytics/pageview")) {
                bucket = pageviewBuckets.computeIfAbsent(ip, k -> newBucket(60, Duration.ofMinutes(1)));
            }

            if (bucket != null && !bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Bucket newBucket(long tokens, Duration duration) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tokens)
                        .refillGreedy(tokens, duration)
                        .build())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
