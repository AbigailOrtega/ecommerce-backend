package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.ForgotPasswordRequest;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.request.ResetPasswordRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.AuthService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;

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
}
