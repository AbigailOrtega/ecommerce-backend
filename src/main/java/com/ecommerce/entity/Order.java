package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @ToString.Exclude
    private User user;

    private String guestEmail;
    private String guestFirstName;
    private String guestLastName;
    private String guestPhone;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    private String paymentMethod;
    private String paymentId;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private String shippingCity;

    @Column(nullable = false)
    private String shippingState;

    @Column(nullable = false)
    private String shippingZipCode;

    @Column(nullable = false)
    private String shippingCountry;

    private String notes;

    private String couponCode;

    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    private String shippingMethodName;

    private String shippingType;           // "NATIONAL" or "PICKUP"
    private String pickupLocationName;     // e.g. "Sucursal Centro, Reforma 42"
    private String pickupTimeSlotLabel;    // e.g. "Lunes 10:00 – 14:00"

    // ── Skydropx ─────────────────────────────────────────────────────────────
    private String skydropxRateId;         // rate chosen by customer at checkout
    private String skydropxShipmentId;
    private String trackingNumber;
    private String carrierName;
    @Column(length = 1024)
    private String labelUrl;
    private String shipmentStatus;        // e.g. "pending", "in_transit", "delivered"

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderNumber == null) {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
