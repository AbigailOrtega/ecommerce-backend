package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false)
    private LocalDate expiresAt;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int usageCount = 0;

    private Integer usageLimit; // null = unlimited

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        code = code.toUpperCase().trim();
    }
}
