package com.ecommerce.repository;

import com.ecommerce.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@DisplayName("OrderItemRepository")
class OrderItemRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired OrderItemRepository orderItemRepository;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        productA = em.persistAndFlush(Product.builder()
                .name("Camiseta").slug("camiseta").sku("SKU-1")
                .price(BigDecimal.valueOf(29.99)).stockQuantity(50).active(true)
                .featured(false).build());

        productB = em.persistAndFlush(Product.builder()
                .name("Pantalón").slug("pantalon").sku("SKU-2")
                .price(BigDecimal.valueOf(59.99)).stockQuantity(10).active(true)
                .featured(false).build());
    }

    // ─── findProductSalesRanked ───────────────────────────────────────────────

    @Nested
    @DisplayName("findProductSalesRanked")
    class FindProductSalesRanked {

        @Test
        @DisplayName("returns aggregated units and revenue per product, ordered descending")
        void returnsAggregatedSalesDescending() {
            Order order = persistOrder(OrderStatus.CONFIRMED);
            persistItem(order, productA, 3, BigDecimal.valueOf(89.97));
            persistItem(order, productB, 1, BigDecimal.valueOf(59.99));

            List<Object[]> rows = orderItemRepository
                    .findProductSalesRanked(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

            assertThat(rows).hasSize(2);
            // productA sold 3 units — should be first (highest)
            Long firstId = (Long) rows.get(0)[0];
            long firstUnits = ((Number) rows.get(0)[2]).longValue();
            assertThat(firstId).isEqualTo(productA.getId());
            assertThat(firstUnits).isEqualTo(3);
        }

        @Test
        @DisplayName("excludes items from CANCELLED orders")
        void excludesCancelledOrders() {
            Order cancelled = persistOrder(OrderStatus.CANCELLED);
            persistItem(cancelled, productA, 10, BigDecimal.valueOf(299.90));

            List<Object[]> rows = orderItemRepository
                    .findProductSalesRanked(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

            assertThat(rows).isEmpty();
        }

        @Test
        @DisplayName("excludes items from REFUNDED orders")
        void excludesRefundedOrders() {
            Order refunded = persistOrder(OrderStatus.REFUNDED);
            persistItem(refunded, productA, 5, BigDecimal.valueOf(149.95));

            List<Object[]> rows = orderItemRepository
                    .findProductSalesRanked(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

            assertThat(rows).isEmpty();
        }

        @Test
        @DisplayName("sums units across multiple orders for the same product")
        void sumsAcrossMultipleOrders() {
            Order order1 = persistOrder(OrderStatus.CONFIRMED);
            Order order2 = persistOrder(OrderStatus.DELIVERED);
            persistItem(order1, productA, 2, BigDecimal.valueOf(59.98));
            persistItem(order2, productA, 3, BigDecimal.valueOf(89.97));

            List<Object[]> rows = orderItemRepository
                    .findProductSalesRanked(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

            assertThat(rows).hasSize(1);
            long totalUnits = ((Number) rows.get(0)[2]).longValue();
            assertThat(totalUnits).isEqualTo(5);
        }

        @Test
        @DisplayName("returns empty list when no order items exist")
        void emptyWhenNoItems() {
            List<Object[]> rows = orderItemRepository
                    .findProductSalesRanked(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

            assertThat(rows).isEmpty();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Order persistOrder(OrderStatus status) {
        Order order = Order.builder()
                .orderNumber("ORD-" + System.nanoTime())
                .status(status)
                .totalAmount(BigDecimal.valueOf(100))
                .shippingAddress("Calle 1").shippingCity("CDMX")
                .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                .shippingType("NATIONAL")
                .build();
        return em.persistAndFlush(order);
    }

    private void persistItem(Order order, Product product, int qty, BigDecimal subtotal) {
        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .productPrice(product.getPrice())
                .quantity(qty)
                .subtotal(subtotal)
                .build();
        em.persistAndFlush(item);
    }
}
