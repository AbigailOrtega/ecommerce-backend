package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.response.CouponResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.CouponService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = CouponController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("CouponController")
class CouponControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CouponService couponService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken mockCustomerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "customer@test.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    private CouponResponse stubCoupon(String code) {
        return new CouponResponse(
                1L,
                code,
                BigDecimal.valueOf(15.00),
                LocalDate.now().plusDays(30),
                true,
                0,
                100,
                LocalDateTime.now()
        );
    }

    // ── POST /api/coupons/validate ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/coupons/validate")
    class ValidateCoupon {

        @Test
        @DisplayName("returns 200 with coupon data for a valid code")
        void validate_200() throws Exception {
            when(couponService.validateCoupon("SAVE15")).thenReturn(stubCoupon("SAVE15"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "SAVE15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value("SAVE15"))
                    .andExpect(jsonPath("$.data.discountPercent").value(15.00))
                    .andExpect(jsonPath("$.data.active").value(true))
                    .andExpect(jsonPath("$.data.usageCount").value(0));
        }

        @Test
        @DisplayName("returns 200 and includes all coupon fields in response")
        void validate_200_fullResponse() throws Exception {
            CouponResponse coupon = new CouponResponse(
                    42L, "PROMO10", BigDecimal.valueOf(10.00),
                    LocalDate.of(2099, 12, 31), true, 5, 50,
                    LocalDateTime.of(2024, 1, 1, 0, 0)
            );
            when(couponService.validateCoupon("PROMO10")).thenReturn(coupon);

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "PROMO10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(42))
                    .andExpect(jsonPath("$.data.code").value("PROMO10"))
                    .andExpect(jsonPath("$.data.discountPercent").value(10.00))
                    .andExpect(jsonPath("$.data.active").value(true))
                    .andExpect(jsonPath("$.data.usageCount").value(5))
                    .andExpect(jsonPath("$.data.usageLimit").value(50));
        }

        @Test
        @DisplayName("returns 400 when coupon code is invalid")
        void validate_400_invalidCode() throws Exception {
            when(couponService.validateCoupon("BADCODE"))
                    .thenThrow(new BadRequestException("Invalid coupon code"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "BADCODE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid coupon code"));
        }

        @Test
        @DisplayName("returns 400 when coupon is inactive")
        void validate_400_inactiveCoupon() throws Exception {
            when(couponService.validateCoupon("INACTIVE"))
                    .thenThrow(new BadRequestException("This coupon is no longer active"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "INACTIVE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("This coupon is no longer active"));
        }

        @Test
        @DisplayName("returns 400 when coupon has expired")
        void validate_400_expiredCoupon() throws Exception {
            when(couponService.validateCoupon("EXPIRED"))
                    .thenThrow(new BadRequestException("This coupon has expired"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "EXPIRED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("This coupon has expired"));
        }

        @Test
        @DisplayName("returns 400 when coupon usage limit has been reached")
        void validate_400_usageLimitReached() throws Exception {
            when(couponService.validateCoupon("MAXUSED"))
                    .thenThrow(new BadRequestException("This coupon has reached its usage limit"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "MAXUSED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("This coupon has reached its usage limit"));
        }

        @Test
        @DisplayName("calls service with the exact code query parameter value")
        void validate_callsServiceWithCode() throws Exception {
            when(couponService.validateCoupon(anyString())).thenReturn(stubCoupon("SAVE15"));

            mockMvc.perform(post("/api/coupons/validate")
                    .principal(mockCustomerAuth())
                    .param("code", "SAVE15"))
                    .andExpect(status().isOk());

            verify(couponService).validateCoupon("SAVE15");
        }
    }
}
