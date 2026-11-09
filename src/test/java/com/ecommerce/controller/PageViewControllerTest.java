package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = PageViewController.class,
    properties = {"app.rate-limit.enabled=true", "app.rate-limit.per-ip-enabled=true"},
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(RateLimiter.class)
@DisplayName("PageViewController")
class PageViewControllerTest {

    private static final String BODY = "{\"path\":\"/products/cojin\",\"sessionId\":\"abc\",\"source\":\"google.com\"}";
    private static final String BROWSER = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Safari/605.1.15";

    @Autowired MockMvc mockMvc;
    @MockitoBean AnalyticsService analyticsService;

    @Test
    @DisplayName("passes path, session, source and user agent; a visitor is not an admin")
    void visitor() throws Exception {
        mockMvc.perform(post("/api/analytics/pageview").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("User-Agent", BROWSER).header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isNoContent());
        verify(analyticsService).recordPageView("/products/cojin", "abc", "google.com", BROWSER, false);
    }

    @Test
    @DisplayName("an administrator's own browsing is flagged so it is not counted")
    void admin() throws Exception {
        var adminAuth = new UsernamePasswordAuthenticationToken("admin@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(post("/api/analytics/pageview").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .principal(adminAuth).header("User-Agent", BROWSER).header("X-Forwarded-For", "198.51.100.2"))
                .andExpect(status().isNoContent());
        verify(analyticsService).recordPageView(anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    @DisplayName("more than 120 page views a minute from one IP get 429")
    void rateLimited() throws Exception {
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(post("/api/analytics/pageview").contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .header("User-Agent", BROWSER).header("X-Forwarded-For", "198.51.100.3"))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/analytics/pageview").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("User-Agent", BROWSER).header("X-Forwarded-For", "198.51.100.3"))
                .andExpect(status().isTooManyRequests());
    }
}
