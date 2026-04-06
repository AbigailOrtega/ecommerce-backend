package com.ecommerce.controller;

import com.ecommerce.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PageViewController {

    private final AnalyticsService analyticsService;

    public record PageViewRequest(String path, String sessionId) {}

    @PostMapping("/pageview")
    public ResponseEntity<Void> record(@RequestBody PageViewRequest req) {
        if (req.path() != null && !req.path().isBlank()) {
            analyticsService.recordPageView(req.path(), req.sessionId());
        }
        return ResponseEntity.ok().build();
    }
}
