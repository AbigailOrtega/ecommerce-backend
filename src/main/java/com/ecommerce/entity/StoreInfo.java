package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "store_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreInfo extends TenantScoped {

    @Id
    /** Same as the store (tenant) id: one row per store. */
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String aboutText;

    @Column(columnDefinition = "TEXT")
    private String mission;

    @Column(columnDefinition = "TEXT")
    private String vision;

    private String phone;

    private String logoUrl;

    private String themeKey;
    private String fontKey;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String privacyPolicy;

    private String instagramUrl;
    private String facebookUrl;
    private String whatsappNumber;
    private Boolean showNameInNavbar;
}
