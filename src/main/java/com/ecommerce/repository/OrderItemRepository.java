package com.ecommerce.repository;

import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.product.id, MIN(oi.productName), SUM(oi.quantity), SUM(oi.subtotal) " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE oi.product IS NOT NULL " +
           "AND o.status NOT IN :excluded " +
           "GROUP BY oi.product.id " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findProductSalesRanked(@Param("excluded") List<OrderStatus> excluded);
}
