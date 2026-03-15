package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_sizes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    @ToString.Exclude
    private ProductColor color;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private Integer stock = 0;
}
