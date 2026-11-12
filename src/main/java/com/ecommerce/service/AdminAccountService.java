package com.ecommerce.service;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is admin. Nobody becomes admin by signing up: the first one comes from ADMIN_EMAIL at
 * startup ({@link AdminBootstrap}) and the rest are invited by an existing admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    /** How long an invitation link (to set the password of a new account) stays valid. */
    static final int INVITATION_HOURS = 48;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /** Makes {@code email} admin, but only while the store has none. Returns whether it did. */
    @Transactional
    public boolean bootstrap(String email) {
        if (adminCount() > 0) return false;
        makeAdmin(normalize(email), "Admin", "Tienda");
        log.info("First admin set up from ADMIN_EMAIL: {}", normalize(email));
        return true;
    }

    /**
     * Makes {@code email} admin. An existing account is promoted and told so; a new one is created
     * without a usable password and sent a link to set it.
     */
    @Transactional
    public User invite(String email, String firstName, String lastName, String invitedBy) {
        String normalized = normalize(email);
        userRepository.findFirstByEmailIgnoreCase(normalized)
                .filter(u -> u.getRole().isStoreAdmin())
                .ifPresent(u -> { throw new BadRequestException("Esta cuenta ya es admin"); });
        User user = makeAdmin(normalized, firstName, lastName);
        log.info("Admin access granted to {} by {}", normalized, invitedBy);
        return user;
    }

    /**
     * Invites {@code email} as admin, or, when it already is one (e.g. the platform owner resending
     * a lost or expired invitation), sends a fresh link to set its password.
     */
    @Transactional
    public User inviteOrResend(String email, String firstName, String lastName) {
        String normalized = normalize(email);
        Optional<User> admin = userRepository.findFirstByEmailIgnoreCase(normalized).filter(u -> u.getRole().isStoreAdmin());
        if (admin.isEmpty()) return makeAdmin(normalized, firstName, lastName);
        User user = admin.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(INVITATION_HOURS));
        userRepository.save(user);
        emailService.sendAdminInvitationEmail(user.getEmail(), user.getFirstName(), token);
        log.info("Admin invitation re-sent to {}", normalized);
        return user;
    }

    /** Takes admin access away. Effective on the user's next request (roles are read per request). */
    @Transactional
    public User revoke(Long userId, String revokedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BadRequestException("El super admin no se quita desde aquí");
        }
        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Esta cuenta no es admin");
        }
        if (user.getEmail().equalsIgnoreCase(revokedBy)) {
            throw new BadRequestException("No puedes quitarte el acceso de admin a ti misma(o)");
        }
        if (adminCount() <= 1) {
            throw new BadRequestException("La tienda necesita al menos un admin");
        }
        user.setRole(Role.CUSTOMER);
        log.info("Admin access revoked from {} by {}", user.getEmail(), revokedBy);
        return userRepository.save(user);
    }

    private User makeAdmin(String email, String firstName, String lastName) {
        // emails are stored as typed at sign-up, so match regardless of case
        Optional<User> existing = userRepository.findFirstByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setRole(Role.ADMIN);
            userRepository.save(user);
            emailService.sendAdminInvitationEmail(user.getEmail(), user.getFirstName(), null);
            return user;
        }

        String token = UUID.randomUUID().toString();
        User user = User.builder()
                .email(email)
                .firstName(orDefault(firstName, "Admin"))
                .lastName(orDefault(lastName, "Tienda"))
                // nobody knows this password: the account is entered after setting one with the link
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.ADMIN)
                .resetToken(token)
                .resetTokenExpiry(LocalDateTime.now().plusHours(INVITATION_HOURS))
                .unsubscribeToken(UUID.randomUUID().toString())
                .build();
        userRepository.save(user);
        emailService.sendAdminInvitationEmail(user.getEmail(), user.getFirstName(), token);
        return user;
    }

    private long adminCount() {
        return userRepository.countByRole(Role.ADMIN) + userRepository.countByRole(Role.SUPER_ADMIN);
    }

    private static String normalize(String email) {
        return email.trim();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
