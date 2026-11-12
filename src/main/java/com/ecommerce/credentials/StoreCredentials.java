package com.ecommerce.credentials;

import com.ecommerce.entity.TenantScoped;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A store's own accounts at Stripe, PayPal and Skydropx: each store charges and ships with its own,
 * never another store's. Columns ending in "_enc" hold secrets encrypted by {@link SecretBox}.
 */
@Entity
@Table(name = "store_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreCredentials extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stripePublicKey;
    @Column(length = 1024)
    private String stripeSecretKeyEnc;
    @Column(length = 1024)
    private String stripeWebhookSecretEnc;

    private String paypalClientId;
    @Column(length = 1024)
    private String paypalClientSecretEnc;
    @Builder.Default
    private boolean paypalLive = false;
    private String paypalWebhookId;

    private String skydropxClientId;
    @Column(length = 1024)
    private String skydropxClientSecretEnc;
    @Builder.Default
    private boolean skydropxSandbox = true;
    @Column(length = 1024)
    private String skydropxWebhookSecretEnc;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
