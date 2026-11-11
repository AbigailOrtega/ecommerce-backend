package com.ecommerce.repository;

import com.ecommerce.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /** Takes a product out of every promotion (the join table has no cascade). */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM promotion_products WHERE product_id = :productId", nativeQuery = true)
    void removeProductFromAll(@org.springframework.data.repository.query.Param("productId") Long productId);


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
