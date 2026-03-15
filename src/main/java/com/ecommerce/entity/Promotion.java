package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "promotions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal discountPercent;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "promotion_products",
        joinColumns = @JoinColumn(name = "promotion_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Product> products = new HashSet<>();
}
