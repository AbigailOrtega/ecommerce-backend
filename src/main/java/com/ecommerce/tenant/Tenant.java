package com.ecommerce.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A store on the platform. Not tenant-scoped itself: the platform reads every store. */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short unique name, e.g. "marea"; used for the platform subdomain. */
    @Column(nullable = false, unique = true, length = 63)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Where the store lives, e.g. "https://www.papeleria.com", for links in e-mails. Null: app.mail.frontend-url. */
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
