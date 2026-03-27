package com.ecommerce.service;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    @InjectMocks private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .firstName("Ana")
                .lastName("López")
                .email("ana@example.com")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
    }

    // ─── register ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register")
    class Register {

        private final RegisterRequest request = new RegisterRequest(
                "Ana", "López", "ana@example.com", "password123", "555-0100", false);

        @Test
        @DisplayName("returns AuthResponse with tokens on success")
        void register_success() {
            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(passwordEncoder.encode(request.password())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtTokenProvider.generateAccessToken(testUser.getEmail())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(testUser.getEmail())).thenReturn("refresh");

            AuthResponse response = authService.register(request);

            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
            assertThat(response.getUser().email()).isEqualTo("ana@example.com");
            verify(emailService).sendWelcomeEmail(testUser);
        }

        @Test
        @DisplayName("throws DuplicateResourceException when email already registered")
        void register_duplicateEmail() {
            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Email already registered");
            verify(userRepository, never()).save(any());
        }
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns AuthResponse with tokens on valid credentials")
        void login_success() {
            LoginRequest request = new LoginRequest("ana@example.com", "password123");
            Authentication auth = mock(Authentication.class);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(auth);
            when(auth.getPrincipal()).thenReturn(testUser);
            when(jwtTokenProvider.generateAccessToken(auth)).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(testUser.getEmail())).thenReturn("refresh");

            AuthResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getUser().email()).isEqualTo("ana@example.com");
        }
    }

    // ─── forgotPassword ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("forgotPassword")
    class ForgotPassword {

        @Test
        @DisplayName("saves token and sends email when user exists")
        void forgotPassword_userFound() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            authService.forgotPassword("ana@example.com");

            verify(userRepository).save(argThat(u ->
                    u.getResetToken() != null && u.getResetTokenExpiry() != null));
            verify(emailService).sendPasswordResetEmail(
                    eq("ana@example.com"), eq("Ana"), anyString());
        }

        @Test
        @DisplayName("does nothing when user does not exist (silent fail)")
        void forgotPassword_userNotFound() {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    authService.forgotPassword("nobody@example.com"));
            verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
        }
    }

    // ─── resetPassword ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("updates password and clears token on valid token")
        void resetPassword_success() {
            testUser.setResetToken("valid-token");
            testUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(20));
            when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("newPass123")).thenReturn("encoded-new");

            authService.resetPassword("valid-token", "newPass123");

            verify(userRepository).save(argThat(u ->
                    u.getPassword().equals("encoded-new") &&
                    u.getResetToken() == null &&
                    u.getResetTokenExpiry() == null));
        }

        @Test
        @DisplayName("throws BadRequestException when token does not exist")
        void resetPassword_invalidToken() {
            when(userRepository.findByResetToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("bad-token", "newPass123"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired token");
        }

        @Test
        @DisplayName("throws BadRequestException when token is expired")
        void resetPassword_expiredToken() {
            testUser.setResetToken("expired-token");
            testUser.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> authService.resetPassword("expired-token", "newPass123"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid or expired token");
            verify(userRepository, never()).save(any());
        }
    }

    // ─── refreshToken ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("returns new tokens when refresh token is valid")
        void refreshToken_success() {
            when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
            when(jwtTokenProvider.getEmailFromToken("refresh")).thenReturn("ana@example.com");
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.generateAccessToken("ana@example.com")).thenReturn("new-access");
            when(jwtTokenProvider.generateRefreshToken("ana@example.com")).thenReturn("new-refresh");

            AuthResponse response = authService.refreshToken("refresh");

            assertThat(response.getAccessToken()).isEqualTo("new-access");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("throws BadRequestException when refresh token is invalid")
        void refreshToken_invalid() {
            when(jwtTokenProvider.validateToken("bad")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("bad"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid refresh token");
        }
    }

    // ─── getCurrentUser ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("returns UserResponse for authenticated user")
        void getCurrentUser_success() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(testUser));

            var response = authService.getCurrentUser("ana@example.com");

            assertThat(response.email()).isEqualTo("ana@example.com");
            assertThat(response.firstName()).isEqualTo("Ana");
        }

        @Test
        @DisplayName("throws BadRequestException when user not found")
        void getCurrentUser_notFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser("ghost@example.com"))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
