package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.ForgotPasswordRequest;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.request.ResetPasswordRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.security.RefreshTokenCookie;
import jakarta.servlet.http.Cookie;
import com.ecommerce.service.AuthService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    properties = {"app.rate-limit.enabled=true", "app.jwt.refresh-token-expiration=7776000000",
                  "app.jwt.refresh-cookie-secure=true"},
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import({RateLimiter.class, RefreshTokenCookie.class})
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;

    private AuthResponse stubAuthResponse() {
        return AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .user(new UserResponse(1L, "Ana", "López", "ana@example.com", null, "CUSTOMER"))
                .build();
    }

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 with AuthResponse on valid request")
        void register_201() throws Exception {
            RegisterRequest req = new RegisterRequest("Ana", "López", "ana@example.com", "password123", null, false);
            when(authService.register(any())).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.user.email").value("ana@example.com"));
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void register_400_validation() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with AuthResponse on valid credentials")
        void login_200() throws Exception {
            LoginRequest req = new LoginRequest("ana@example.com", "password123");
            when(authService.login(any())).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"));
        }

        private org.springframework.test.web.servlet.ResultActions login(String email, String ip) throws Exception {
            return mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-pass"))));
        }

        @Test
        @DisplayName("returns 429 after 10 failed attempts from the same IP; other IPs are unaffected")
        void login_429_afterTenFailuresPerIp() throws Exception {
            when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

            for (int i = 0; i < 10; i++) {
                login("user" + i + "@example.com", "203.0.113.10").andExpect(status().isUnauthorized());
            }
            login("other@example.com", "203.0.113.10")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.message").value("Demasiados intentos. Espera unos minutos e intenta de nuevo."));

            login("other@example.com", "198.51.100.7").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("successful logins do not count towards the limit")
        void login_successDoesNotCount() throws Exception {
            when(authService.login(any())).thenReturn(stubAuthResponse());

            for (int i = 0; i < 15; i++) {
                login("ana@example.com", "203.0.113.20").andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("returns 429 after 20 failed attempts on the same email from many IPs")
        void login_429_afterTwentyFailuresPerEmail() throws Exception {
            when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

            for (int i = 0; i < 20; i++) {
                login("victim@example.com", "192.0.2." + i).andExpect(status().isUnauthorized());
            }
            login("victim@example.com", "192.0.2.99").andExpect(status().isTooManyRequests());
        }
    }

    // ─── POST /api/auth/forgot-password ──────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPassword {

        @Test
        @DisplayName("returns 200 with generic message")
        void forgotPassword_200() throws Exception {
            ForgotPasswordRequest req = new ForgotPasswordRequest("ana@example.com");
            doNothing().when(authService).forgotPassword(anyString());

            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("returns 400 when email is invalid")
        void forgotPassword_400_invalidEmail() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"not-an-email\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 429 after 3 requests for the same email in an hour")
        void forgotPassword_429_perEmail() throws Exception {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "203.0.113." + (50 + i))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mailbomb@example.com\"}"))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(post("/api/auth/forgot-password")
                    .header("X-Forwarded-For", "203.0.113.60")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"mailbomb@example.com\"}"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("returns 429 after 5 requests from the same IP in an hour")
        void forgotPassword_429_perIp() throws Exception {
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "203.0.113.70")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"u" + i + "@example.com\"}"))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(post("/api/auth/forgot-password")
                    .header("X-Forwarded-For", "203.0.113.70")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"u9@example.com\"}"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ─── POST /api/auth/reset-password ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("returns 200 on valid token and password")
        void resetPassword_200() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest("uuid-token", "newPassword123");
            doNothing().when(authService).resetPassword(anyString(), anyString());

            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("returns 400 when password is too short")
        void resetPassword_400_shortPassword() throws Exception {
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"tok\", \"newPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── Refresh token cookie ─────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh token cookie")
    class RefreshCookie {

        @Test
        @DisplayName("login sets an HttpOnly, Secure, SameSite=Strict cookie and leaves the token out of the body")
        void login_setsCookie() throws Exception {
            when(authService.login(any())).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest("ana@example.com", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().string("Set-Cookie", allOf(
                            org.hamcrest.Matchers.startsWith("refresh_token=refresh-token;"),
                            containsString("Path=/api/auth"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=Strict"),
                            containsString("Max-Age=7776000"))));
        }

        @Test
        @DisplayName("register also sets the cookie")
        void register_setsCookie() throws Exception {
            when(authService.register(any())).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new RegisterRequest("Ana", "López", "ana@example.com", "password123", null, false))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("refresh_token=refresh-token;")));
        }

        @Test
        @DisplayName("refresh reads the token from the cookie and rotates it")
        void refresh_fromCookie() throws Exception {
            when(authService.refreshToken("from-cookie")).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "from-cookie")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("refresh_token=refresh-token;")));
        }

        @Test
        @DisplayName("refresh accepts a token in the body from sessions opened before the cookie")
        void refresh_legacyBody() throws Exception {
            when(authService.refreshToken("from-body")).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"from-body\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("refresh_token=refresh-token;")));
        }

        @Test
        @DisplayName("the cookie wins over a token in the body")
        void refresh_cookieWins() throws Exception {
            when(authService.refreshToken("from-cookie")).thenReturn(stubAuthResponse());

            mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new Cookie("refresh_token", "from-cookie"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"from-body\"}"))
                    .andExpect(status().isOk());
            verify(authService).refreshToken("from-cookie");
        }

        @Test
        @DisplayName("logout expires the cookie")
        void logout_clearsCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", allOf(
                            org.hamcrest.Matchers.startsWith("refresh_token=;"), containsString("Max-Age=0"), containsString("Path=/api/auth"))));
        }
    }
}
