package com.ecommerce.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories", uniqueConstraints = {@UniqueConstraint(name = "uk_categories_tenant_slug", columnNames = {"tenant_id", "slug"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String description;

    private String imageUrl;

    @Column
    private String slug;

    /** false hides it from the store (menu, filters, product labels); the admin still uses it */
    @Builder.Default
    @Column(nullable = false)
    private boolean visible = true;

    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (slug == null || slug.isBlank()) {
            slug = com.ecommerce.util.Slugs.slugify(name);
        }
    }
}
