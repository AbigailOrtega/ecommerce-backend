package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "shipping_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingConfig {

    @Id
    private Long id = 1L;

    @Builder.Default
    private boolean nationalEnabled = true;

    @Builder.Default
    private boolean pickupEnabled = true;

    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal nationalBasePrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal nationalPricePerKm = BigDecimal.ZERO;

    private String originAddress;

    private String googleMapsApiKey;

    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal pickupCost = BigDecimal.ZERO;

    // ── Skydropx ─────────────────────────────────────────────────────────────
    private String skydropxClientId;
    private String skydropxClientSecret;

    // Dirección de origen para guías
    private String skydropxOriginStreet;
    private String skydropxOriginPostalCode;
    private String skydropxOriginCity;
    private String skydropxOriginState;
    @Builder.Default
    private String skydropxOriginCountry = "MX";
    private String skydropxSenderName;
    private String skydropxSenderEmail;
    private String skydropxSenderPhone;

    // Dimensiones de paquete por defecto
    @Builder.Default
    private Double skydropxDefaultWeight = 1.0;
    @Builder.Default
    private Integer skydropxDefaultLength = 30;
    @Builder.Default
    private Integer skydropxDefaultWidth = 20;
    @Builder.Default
    private Integer skydropxDefaultHeight = 15;
}
