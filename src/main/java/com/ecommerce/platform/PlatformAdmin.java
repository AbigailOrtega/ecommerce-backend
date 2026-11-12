package com.ecommerce.platform;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A super admin: runs the platform (every store). Not a user of any store, so not tenant-scoped.
 * Added by hand in the database with only the e-mail; the password is set through the e-mailed link.
 */
@Entity
@Table(name = "platform_admins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** BCrypt; null until the owner sets it with the e-mailed link. */
    private String password;

    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
