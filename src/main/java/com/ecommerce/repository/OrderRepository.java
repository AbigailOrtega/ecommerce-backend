package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByPaymentId(String paymentId);
    Optional<Order> findBySkydropxShipmentId(String skydropxShipmentId);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "SELECT o.* FROM orders o LEFT JOIN users u ON u.id = o.user_id WHERE " +
           "(CAST(:status AS varchar) IS NULL OR o.status = CAST(:status AS varchar)) AND " +
           "(CAST(:shippingType AS varchar) IS NULL OR o.shipping_type = CAST(:shippingType AS varchar)) AND " +
           "(CAST(:paymentMethod AS varchar) IS NULL OR LOWER(o.payment_method) = CAST(:paymentMethod AS varchar)) AND " +
           "(CAST(:dateFrom AS timestamp) IS NULL OR o.created_at >= CAST(:dateFrom AS timestamp)) AND " +
           "(CAST(:dateTo AS timestamp) IS NULL OR o.created_at <= CAST(:dateTo AS timestamp)) AND " +
           "(CAST(:search AS varchar) IS NULL OR " +
           "  LOWER(o.order_number) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(u.email,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(o.guest_first_name,'') || ' ' || COALESCE(o.guest_last_name,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(o.guest_email,'')) LIKE '%' || CAST(:search AS varchar) || '%') " +
           "ORDER BY o.created_at DESC",
           countQuery = "SELECT COUNT(o.id) FROM orders o LEFT JOIN users u ON u.id = o.user_id WHERE " +
           "(CAST(:status AS varchar) IS NULL OR o.status = CAST(:status AS varchar)) AND " +
           "(CAST(:shippingType AS varchar) IS NULL OR o.shipping_type = CAST(:shippingType AS varchar)) AND " +
           "(CAST(:paymentMethod AS varchar) IS NULL OR LOWER(o.payment_method) = CAST(:paymentMethod AS varchar)) AND " +
           "(CAST(:dateFrom AS timestamp) IS NULL OR o.created_at >= CAST(:dateFrom AS timestamp)) AND " +
           "(CAST(:dateTo AS timestamp) IS NULL OR o.created_at <= CAST(:dateTo AS timestamp)) AND " +
           "(CAST(:search AS varchar) IS NULL OR " +
           "  LOWER(o.order_number) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(u.email,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(o.guest_first_name,'') || ' ' || COALESCE(o.guest_last_name,'')) LIKE '%' || CAST(:search AS varchar) || '%' OR " +
           "  LOWER(COALESCE(o.guest_email,'')) LIKE '%' || CAST(:search AS varchar) || '%')",
           nativeQuery = true)
    Page<Order> findAllWithFilters(
            @Param("status") String status,
            @Param("shippingType") String shippingType,
            @Param("paymentMethod") String paymentMethod,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'REFUNDED')")
    BigDecimal sumTotalRevenue();

    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findByGuestEmailAndUserIsNullOrderByCreatedAtDesc(String guestEmail);

    List<Order> findByShippingTypeAndStatusInOrderByCreatedAtAsc(String shippingType, List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.pickupLocationId = :locationId AND o.pickupDate = :date AND o.status NOT IN :excluded")
    long countPickupOrdersForDate(@Param("locationId") Long locationId,
                                  @Param("date") LocalDate date,
                                  @Param("excluded") List<OrderStatus> excluded);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.pickupLocationId = :locationId AND o.pickupDate = :date AND o.pickupAvailabilityId = :availabilityId AND o.status NOT IN :excluded")
    long countPickupOrdersForRule(@Param("locationId") Long locationId,
                                  @Param("date") LocalDate date,
                                  @Param("availabilityId") Long availabilityId,
                                  @Param("excluded") List<OrderStatus> excluded);

    @Query("SELECT o FROM Order o WHERE o.pickupAvailabilityId = :availabilityId AND o.pickupDate = :date AND o.status NOT IN :excluded")
    List<Order> findAffectedPickupOrders(@Param("availabilityId") Long availabilityId,
                                         @Param("date") LocalDate date,
                                         @Param("excluded") List<OrderStatus> excluded);
}
