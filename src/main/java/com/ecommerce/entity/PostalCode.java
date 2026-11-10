package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One settlement (colonia) of Mexico's postal code catalog; see PostalCodeCatalogService. */
@Entity
@Table(name = "postal_codes", indexes = @Index(name = "idx_postal_codes_code", columnList = "code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostalCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5)
    private String code;

    @Column(nullable = false, length = 150)
    private String settlement;

    @Column(name = "settlement_type", length = 60)
    private String settlementType;

    @Column(nullable = false, length = 120)
    private String municipality;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(length = 120)
    private String city;
}
