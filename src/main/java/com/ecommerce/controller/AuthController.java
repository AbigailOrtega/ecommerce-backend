package com.ecommerce.controller;

import com.ecommerce.dto.request.ForgotPasswordRequest;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.request.ResetPasswordRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.ratelimit.ClientIp;
import com.ecommerce.ratelimit.RateLimit;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth endpoints")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for email: {}", request.email());
        AuthResponse response = authService.register(request);
        log.info("Registration successful for email: {}", request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        String ip = ClientIp.of(httpRequest);
        log.info("Login request received for email: {} from {}", request.email(), ip);
        rateLimiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        rateLimiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_EMAIL, request.email());
        AuthResponse response;
        try {
            response = authService.login(request);
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_IP, ip);
            rateLimiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_EMAIL, request.email());
            throw e;
        }
        rateLimiter.reset(RateLimit.LOGIN_FAILURES_PER_EMAIL, request.email());
        log.info("Login successful for email: {}", request.email());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                            HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.FORGOT_PASSWORD_PER_IP, ClientIp.of(httpRequest));
        rateLimiter.consume(RateLimit.FORGOT_PASSWORD_PER_EMAIL, request.email());
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.<Void>success("If an account with that email exists, a reset link has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.<Void>success("Password has been reset successfully.", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        UserResponse response = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
