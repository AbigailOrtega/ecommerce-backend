package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user_id", columnList = "user_id"),
    @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
    @Index(name = "idx_orders_created_at", columnList = "created_at"),
    @Index(name = "idx_orders_payment_id", columnList = "payment_id"),
    @Index(name = "idx_orders_guest_email", columnList = "guest_email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends TenantScoped {

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

    /** The colonia (national shipping); null for pickup and for orders from before it was asked. */
    @Column(length = 150)
    private String shippingNeighborhood;

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
    private LocalDate pickupDate;          // concrete date chosen by buyer
    private Long pickupLocationId;         // denormalized for capacity queries
    private Long pickupAvailabilityId;     // specific rule chosen when multiple slots exist
    @Builder.Default
    private boolean pickupCancelled = false; // set true when admin blocks the pickup date

    /**
     * Secret in the link of the pickup emails: whoever has it can reschedule or cancel the
     * pickup (guests have no account). Never sent in API responses.
     */
    @Column(name = "manage_token", length = 36)
    private String manageToken;

    /** The token, created on the spot for orders placed before it existed. */
    public String ensureManageToken() {
        if (manageToken == null) manageToken = UUID.randomUUID().toString();
        return manageToken;
    }

    // ── Accounting ───────────────────────────────────────────────────────────
    /** When the money came in (payment confirmed, or the admin marked it paid). Null: never paid. */
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    /** Commission charged by Stripe / PayPal; null until read from the provider. */
    @Column(precision = 10, scale = 2)
    private BigDecimal paymentFee;
    /** Lookups of the commission that came back empty; the job gives up after a few. */
    @Column(nullable = false)
    private int paymentFeeAttempts;
    /** What Skydropx charged for the label. */
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingLabelCost;

    /** Records the payment once (a later status change does not move the date). */
    public void markPaidAt(LocalDateTime when) {
        if (paidAt == null) paidAt = when;
    }

    public void markRefundedAt(LocalDateTime when) {
        if (refundedAt == null) refundedAt = when;
    }

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
        ensureManageToken();
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
