package com.ecommerce.service;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthResponse register(RegisterRequest request) {
        log.debug("Checking if email already exists: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed - email already registered: {}", request.email());
            throw new DuplicateResourceException("Email already registered");
        }

        log.debug("Creating new user with role CUSTOMER");
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(Role.CUSTOMER)
                .marketingOptIn(request.marketingOptIn())
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();

        user = userRepository.save(user);
        log.info("User saved to database with id: {}", user.getId());

        linkGuestOrdersToUser(user);
        emailService.sendWelcomeEmail(user);

        log.debug("Generating JWT tokens for user: {}", user.getEmail());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        log.info("Registration completed successfully for user: {}", user.getEmail());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(mapToUserResponse(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.debug("Authenticating user: {}", request.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        log.debug("Authentication successful for: {}", request.email());

        User user = (User) authentication.getPrincipal();
        log.debug("User retrieved - id: {}, role: {}", user.getId(), user.getRole());

        linkGuestOrdersToUser(user);

        log.debug("Generating JWT tokens");
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        log.info("Login completed successfully for user: {} (role: {})", user.getEmail(), user.getRole());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(mapToUserResponse(user))
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(email);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(mapToUserResponse(user))
                .build();
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return mapToUserResponse(user);
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), token);
        });
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired token"));
        if (!user.getResetTokenExpiry().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired token");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    private void linkGuestOrdersToUser(User user) {
        List<Order> orders = orderRepository.findByGuestEmailAndUserIsNullOrderByCreatedAtDesc(user.getEmail());
        orders.forEach(o -> { o.setUser(user); o.setGuestEmail(null); });
        if (!orders.isEmpty()) orderRepository.saveAll(orders);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name()
        );
    }
}
