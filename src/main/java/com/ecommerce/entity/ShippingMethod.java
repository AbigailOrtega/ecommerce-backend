package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "shipping_methods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private Integer estimatedDays;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int displayOrder = 0;
}
