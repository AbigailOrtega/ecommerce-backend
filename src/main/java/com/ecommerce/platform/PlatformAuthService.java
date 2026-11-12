package com.ecommerce.platform;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.security.JwtTokenProvider;
import com.ecommerce.service.EmailService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Super admins' sessions: log in, set or reset the password by e-mailed link, check a session. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthService {

    static final int LINK_HOURS = 2;
    private static final String WRONG_LOGIN = "Correo o contraseña incorrectos";
    private static final String BAD_LINK = "El enlace no es válido o ya expiró. Solicita uno nuevo.";

    private final PlatformAdminRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final EmailService emailService;
    private final PlatformHost platformHost;

    public record Session(String token, String email) {}

    public Session login(String email, String password) {
        PlatformAdmin admin = repository.findByEmailIgnoreCase(email.trim())
                .filter(a -> a.getPassword() != null && passwordEncoder.matches(password, a.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, WRONG_LOGIN));
        return new Session(jwt.generatePlatformToken(admin.getEmail(), admin.getPassword()), admin.getEmail());
    }

    /** Sends the link to set the password, if the account exists (the answer is the same either way). */
    @Transactional
    public void forgotPassword(String email) {
        repository.findByEmailIgnoreCase(email.trim()).ifPresent(admin -> {
            String token = UUID.randomUUID().toString();
            admin.setResetToken(token);
            admin.setResetTokenExpiry(LocalDateTime.now().plusHours(LINK_HOURS));
            repository.save(admin);
            emailService.sendPlatformPasswordEmail(admin.getEmail(), platformHost.url() + "/plataforma?token=" + token);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PlatformAdmin admin = repository.findByResetToken(token)
                .filter(a -> a.getResetTokenExpiry() != null && a.getResetTokenExpiry().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadRequestException(BAD_LINK));
        admin.setPassword(passwordEncoder.encode(newPassword));
        admin.setResetToken(null);
        admin.setResetTokenExpiry(null);
        repository.save(admin);
        log.info("Platform password set for {}", admin.getEmail());
    }

    /** The super admin behind "Bearer …", or 401: no token, a store's token, expired, or the password changed since. */
    public PlatformAdmin authenticate(String authorization) {
        return Optional.ofNullable(authorization)
                .filter(h -> h.startsWith("Bearer "))
                .flatMap(h -> jwt.parseClaims(h.substring(7)))
                .filter(jwt::isPlatformToken)
                .flatMap(this::accountFor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Optional<PlatformAdmin> accountFor(Claims claims) {
        return repository.findByEmailIgnoreCase(claims.getSubject())
                .filter(a -> a.getPassword() != null && jwt.isCurrentForPassword(claims, a.getPassword()));
    }
}
