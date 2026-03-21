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
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

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
