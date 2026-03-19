package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "store_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreInfo {

    @Id
    private Long id = 1L;

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

    private String instagramUrl;
    private String facebookUrl;
}
