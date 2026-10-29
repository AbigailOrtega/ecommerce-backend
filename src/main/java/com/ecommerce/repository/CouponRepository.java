package com.ecommerce.repository;

import com.ecommerce.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /** Atomically counts one use; returns 0 (and changes nothing) if the usage limit is reached. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usageCount = c.usageCount + 1 WHERE c.id = :id AND (c.usageLimit IS NULL OR c.usageCount < c.usageLimit)")
    int incrementUsageIfAvailable(@Param("id") Long id);
}
