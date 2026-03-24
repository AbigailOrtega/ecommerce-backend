package com.ecommerce.repository;

import com.ecommerce.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserId(Long userId);
    Optional<CartItem> findByUserIdAndProductIdAndSelectedSizeIsNull(Long userId, Long productId);
    Optional<CartItem> findByUserIdAndProductIdAndSelectedSizeId(Long userId, Long productId, Long selectedSizeId);
    void deleteByUserId(Long userId);

    @Modifying
    @Query("UPDATE CartItem c SET c.selectedSize = null WHERE c.selectedSize.id IN " +
           "(SELECT s.id FROM ProductSize s WHERE s.color.product.id = :productId)")
    void clearSelectedSizeByProductId(@Param("productId") Long productId);
}
