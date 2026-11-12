package com.ecommerce.platform;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.ratelimit.RateLimit;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.ratelimit.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The super admins' login, on the platform's domain only (elsewhere a 404). */
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService auth;
    private final PlatformHost platformHost;
    private final RateLimiter rateLimiter;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PlatformAuthService.Session>> login(@Valid @RequestBody LoginRequest request,
                                                                          HttpServletRequest http) {
        platformHost.require(http);
        String ip = ClientIp.of(http);
        String key = "platform:" + request.email().trim().toLowerCase();
        rateLimiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        rateLimiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_EMAIL, key);
        try {
            PlatformAuthService.Session session = auth.login(request.email(), request.password());
            rateLimiter.reset(RateLimit.LOGIN_FAILURES_PER_EMAIL, key);
            return ResponseEntity.ok(ApiResponse.success(session));
        } catch (ResponseStatusException e) {
            rateLimiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_IP, ip);
            rateLimiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_EMAIL, key);
            throw e;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        platformHost.require(http);
        rateLimiter.consume(RateLimit.FORGOT_PASSWORD_PER_IP, ClientIp.of(http));
        rateLimiter.consume(RateLimit.FORGOT_PASSWORD_PER_EMAIL, "platform:" + request.email().trim().toLowerCase());
        auth.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.success("Si la cuenta existe, te enviamos un enlace para poner tu contraseña.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
        platformHost.require(http);
        auth.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Contraseña guardada. Ya puedes entrar.", null));
    }
}
