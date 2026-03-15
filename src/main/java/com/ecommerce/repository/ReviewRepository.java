package com.ecommerce.repository;

import com.ecommerce.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdAndApprovedTrue(Long productId, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    List<Review> findAllByApprovedFalseOrderByCreatedAtDesc();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.approved = true GROUP BY r.rating")
    List<Object[]> findRatingDistributionByProductId(@Param("productId") Long productId);

    long countByProductIdAndApprovedTrue(Long productId);
}
