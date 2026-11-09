package com.ecommerce.controller;

import com.ecommerce.ratelimit.ClientIp;
import com.ecommerce.ratelimit.RateLimit;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PageViewController {

    private final AnalyticsService analyticsService;
    private final RateLimiter rateLimiter;

    /** source: utm_source or referring site, sent with the first page of a visit only. */
    public record PageViewRequest(String path, String sessionId, String source) {}

    /** Always 204 (also when the view is not counted): the store never waits on or shows analytics. */
    @PostMapping("/pageview")
    public ResponseEntity<Void> record(@RequestBody PageViewRequest req, HttpServletRequest http, Authentication auth) {
        rateLimiter.consume(RateLimit.PAGE_VIEW_PER_IP, ClientIp.of(http));
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        try {
            analyticsService.recordPageView(req.path(), req.sessionId(), req.source(),
                    http.getHeader(HttpHeaders.USER_AGENT), admin);
        } catch (RuntimeException e) {
            log.warn("Could not record page view: {}", e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
