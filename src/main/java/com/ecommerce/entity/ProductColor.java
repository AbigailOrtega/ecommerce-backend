package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_colors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "product_color_images", joinColumns = @JoinColumn(name = "color_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @OneToMany(mappedBy = "color", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductSize> sizes = new ArrayList<>();
}
