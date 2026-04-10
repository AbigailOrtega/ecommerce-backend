package com.ecommerce.controller;

import com.ecommerce.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PageViewController {

    private final AnalyticsService analyticsService;

    public record PageViewRequest(String path, String sessionId) {}

    @PostMapping("/pageview")
    public ResponseEntity<Void> record(@RequestBody PageViewRequest req) {
        log.info("POST /api/analytics/pageview - path={} sessionId={}", req.path(), req.sessionId());
        try {
            if (req.path() != null && !req.path().isBlank()) {
                analyticsService.recordPageView(req.path(), req.sessionId());
                log.info("POST /api/analytics/pageview - recorded successfully");
            } else {
                log.warn("POST /api/analytics/pageview - skipped (blank path)");
            }
        } catch (Exception e) {
            log.error("POST /api/analytics/pageview - error recording pageview: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
