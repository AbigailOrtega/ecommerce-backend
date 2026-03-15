package com.ecommerce.repository;

import com.ecommerce.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("""
        SELECT p FROM Promotion p JOIN p.products prod
        WHERE prod.id = :productId
          AND p.active = true
          AND p.startDate <= :today
          AND p.endDate >= :today
        ORDER BY p.discountPercent DESC
        LIMIT 1
    """)
    Optional<Promotion> findBestActivePromotion(Long productId, LocalDate today);
}
