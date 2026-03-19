package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "store_image")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;

    @Builder.Default
    private int displayOrder = 0;

    @Builder.Default
    private boolean active = true;
}
