package com.ecommerce.repository;

import com.ecommerce.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserId(Long userId);
    Optional<CartItem> findByUserIdAndProductIdAndSelectedSizeIsNull(Long userId, Long productId);
    Optional<CartItem> findByUserIdAndProductIdAndSelectedSizeId(Long userId, Long productId, Long selectedSizeId);
    void deleteByUserId(Long userId);
}
