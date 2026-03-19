package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.response.PromoBannerResponse;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.PromoBannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = BannerController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("BannerController")
class BannerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PromoBannerService promoBannerService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PromoBannerResponse stubBanner(Long id, String imageUrl, String linkUrl) {
        return new PromoBannerResponse(id, imageUrl, linkUrl, true, LocalDateTime.of(2024, 3, 1, 9, 0));
    }

    // ─── GET /api/banner/active ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/banner/active")
    class GetActiveBanners {

        @Test
        @DisplayName("returns 200 with list of active banners")
        void getActive_200() throws Exception {
            when(promoBannerService.getActiveBanners()).thenReturn(List.of(
                    stubBanner(1L, "https://img.example.com/banner1.jpg", "https://shop.example.com/sale"),
                    stubBanner(2L, "https://img.example.com/banner2.jpg", null)));

            mockMvc.perform(get("/api/banner/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].imageUrl").value("https://img.example.com/banner1.jpg"))
                    .andExpect(jsonPath("$.data[0].linkUrl").value("https://shop.example.com/sale"))
                    .andExpect(jsonPath("$.data[0].active").value(true))
                    .andExpect(jsonPath("$.data[1].id").value(2));
        }

        @Test
        @DisplayName("returns 200 with empty list when no active banners exist")
        void getActive_200_empty() throws Exception {
            when(promoBannerService.getActiveBanners()).thenReturn(List.of());

            mockMvc.perform(get("/api/banner/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("calls promoBannerService.getActiveBanners exactly once")
        void getActive_callsService() throws Exception {
            when(promoBannerService.getActiveBanners()).thenReturn(List.of());

            mockMvc.perform(get("/api/banner/active"))
                    .andExpect(status().isOk());

            verify(promoBannerService, times(1)).getActiveBanners();
        }

        @Test
        @DisplayName("returns banner with null linkUrl when linkUrl is absent")
        void getActive_200_nullLinkUrl() throws Exception {
            when(promoBannerService.getActiveBanners()).thenReturn(
                    List.of(new PromoBannerResponse(3L, "https://img.example.com/promo.jpg",
                            null, true, LocalDateTime.of(2024, 5, 10, 12, 0))));

            mockMvc.perform(get("/api/banner/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(3))
                    .andExpect(jsonPath("$.data[0].imageUrl").value("https://img.example.com/promo.jpg"))
                    // linkUrl is null — JsonInclude.NON_NULL means the field is omitted from JSON
                    .andExpect(jsonPath("$.data[0].linkUrl").doesNotExist());
        }
    }
}
