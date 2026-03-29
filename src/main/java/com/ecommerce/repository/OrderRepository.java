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

    @Query("SELECT o FROM Order o LEFT JOIN o.user u WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:shippingType IS NULL OR o.shippingType = :shippingType) AND " +
           "(:paymentMethod IS NULL OR LOWER(o.paymentMethod) = :paymentMethod) AND " +
           "(:dateFrom IS NULL OR o.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR o.createdAt <= :dateTo) AND " +
           "(:search IS NULL OR " +
           "  LOWER(o.orderNumber) LIKE CONCAT('%', :search, '%') OR " +
           "  LOWER(CONCAT(COALESCE(u.firstName,''), ' ', COALESCE(u.lastName,''))) LIKE CONCAT('%', :search, '%') OR " +
           "  LOWER(COALESCE(u.email,'')) LIKE CONCAT('%', :search, '%') OR " +
           "  LOWER(CONCAT(COALESCE(o.guestFirstName,''), ' ', COALESCE(o.guestLastName,''))) LIKE CONCAT('%', :search, '%') OR " +
           "  LOWER(COALESCE(o.guestEmail,'')) LIKE CONCAT('%', :search, '%')) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findAllWithFilters(
            @Param("status") com.ecommerce.entity.OrderStatus status,
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
