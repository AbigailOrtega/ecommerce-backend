package com.ecommerce.service;

import com.ecommerce.dto.response.InventoryItem;
import com.ecommerce.dto.response.ProductSalesItem;
import com.ecommerce.dto.response.SalesReportResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private ReportService reportService;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        productA = Product.builder()
                .id(1L).name("Camiseta").sku("SKU-1")
                .price(BigDecimal.valueOf(29.99)).stockQuantity(50).active(true).build();
        productB = Product.builder()
                .id(2L).name("Pantalón").sku("SKU-2")
                .price(BigDecimal.valueOf(59.99)).stockQuantity(10).active(true).build();
    }

    // ─── getSalesReport ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSalesReport")
    class GetSalesReport {

        @Test
        @DisplayName("returns 'Última semana' label for period=week")
        void week_returnsCorrectPeriodLabel() {
            when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("week");

            assertThat(result.period()).isEqualTo("Última semana");
        }

        @Test
        @DisplayName("returns 'Este año' label for period=year")
        void year_returnsCorrectPeriodLabel() {
            when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("year");

            assertThat(result.period()).isEqualTo("Este año");
        }

        @Test
        @DisplayName("defaults to 'Este mes' for unknown period")
        void unknown_defaultsToMonth() {
            when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("unknown");

            assertThat(result.period()).isEqualTo("Este mes");
        }

        @Test
        @DisplayName("excludes CANCELLED orders from revenue and order count")
        void excludesCancelledOrders() {
            Order active = buildOrder(1L, OrderStatus.CONFIRMED, BigDecimal.valueOf(100));
            Order cancelled = buildOrder(2L, OrderStatus.CANCELLED, BigDecimal.valueOf(50));
            when(orderRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of(active, cancelled));

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(result.totalOrders()).isEqualTo(1);
        }

        @Test
        @DisplayName("excludes REFUNDED orders from revenue")
        void excludesRefundedOrders() {
            Order refunded = buildOrder(1L, OrderStatus.REFUNDED, BigDecimal.valueOf(80));
            when(orderRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of(refunded));

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalOrders()).isZero();
        }

        @Test
        @DisplayName("aggregates two orders on the same day into one data point")
        void aggregatesSameDayOrders() {
            Order o1 = buildOrder(1L, OrderStatus.CONFIRMED, BigDecimal.valueOf(30));
            Order o2 = buildOrder(2L, OrderStatus.CONFIRMED, BigDecimal.valueOf(20));
            when(orderRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of(o1, o2));

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).orderCount()).isEqualTo(2);
            assertThat(result.data().get(0).revenue()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        @DisplayName("returns zero totals and empty data when no orders")
        void emptyOrders_returnsZeroTotals() {
            when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.data()).isEmpty();
            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalOrders()).isZero();
        }
    }

    // ─── getTopSellingProducts ────────────────────────────────────────────────

    @Nested
    @DisplayName("getTopSellingProducts")
    class GetTopSellingProducts {

        @Test
        @DisplayName("returns products in descending units-sold order from ranked query")
        void returnsSortedByUnitsSold() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 100L, BigDecimal.valueOf(2999.00)},
                    new Object[]{2L, "Pantalón", 50L,  BigDecimal.valueOf(2999.50)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));
            when(productRepository.findById(2L)).thenReturn(Optional.of(productB));

            List<ProductSalesItem> result = reportService.getTopSellingProducts(20);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).unitsSold()).isEqualTo(100);
            assertThat(result.get(1).unitsSold()).isEqualTo(50);
        }

        @Test
        @DisplayName("respects limit parameter — truncates to requested count")
        void respectsLimit() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 100L, BigDecimal.valueOf(2999.00)},
                    new Object[]{2L, "Pantalón", 50L,  BigDecimal.valueOf(1999.00)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));

            List<ProductSalesItem> result = reportService.getTopSellingProducts(1);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("maps revenue and current stock from repository correctly")
        void mapsFields() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 10L, BigDecimal.valueOf(299.90)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));

            List<ProductSalesItem> result = reportService.getTopSellingProducts(20);

            assertThat(result.get(0).productId()).isEqualTo(1L);
            assertThat(result.get(0).productName()).isEqualTo("Camiseta");
            assertThat(result.get(0).unitsSold()).isEqualTo(10);
            assertThat(result.get(0).revenue()).isEqualByComparingTo(BigDecimal.valueOf(299.90));
            assertThat(result.get(0).currentStock()).isEqualTo(50);
        }

        @Test
        @DisplayName("returns empty list when there are no sales")
        void emptyWhenNoSales() {
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(List.of());

            List<ProductSalesItem> result = reportService.getTopSellingProducts(20);

            assertThat(result).isEmpty();
        }
    }

    // ─── getLeastSellingProducts ──────────────────────────────────────────────

    @Nested
    @DisplayName("getLeastSellingProducts")
    class GetLeastSellingProducts {

        @Test
        @DisplayName("includes products with zero sales in result")
        void includesZeroSalesProducts() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 10L, BigDecimal.valueOf(299.90)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));
            when(productRepository.findAll()).thenReturn(List.of(productA, productB));

            List<ProductSalesItem> result = reportService.getLeastSellingProducts(20);

            assertThat(result).anyMatch(p -> p.productId().equals(2L) && p.unitsSold() == 0);
        }

        @Test
        @DisplayName("sorts result ascending by units sold — least sold comes first")
        void sortedAscending() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 50L, BigDecimal.valueOf(1499.50)},
                    new Object[]{2L, "Pantalón",  5L, BigDecimal.valueOf(299.95)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));
            when(productRepository.findById(2L)).thenReturn(Optional.of(productB));
            when(productRepository.findAll()).thenReturn(List.of(productA, productB));

            List<ProductSalesItem> result = reportService.getLeastSellingProducts(20);

            assertThat(result.get(0).unitsSold())
                    .isLessThanOrEqualTo(result.get(result.size() - 1).unitsSold());
        }

        @Test
        @DisplayName("zero-sales products appear before products with any sales")
        void zeroSalesAppearsFirst() {
            List<Object[]> rows = List.<Object[]>of(
                    new Object[]{1L, "Camiseta", 5L, BigDecimal.valueOf(149.95)}
            );
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));
            when(productRepository.findAll()).thenReturn(List.of(productA, productB));

            List<ProductSalesItem> result = reportService.getLeastSellingProducts(20);

            assertThat(result.get(0).unitsSold()).isZero();
        }
    }

    // ─── getInventoryReport ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getInventoryReport")
    class GetInventoryReport {

        @Test
        @DisplayName("returns all products mapped to InventoryItem")
        void returnsAllProducts() {
            when(productRepository.findAll()).thenReturn(List.of(productA, productB));

            List<InventoryItem> result = reportService.getInventoryReport();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("sorts products alphabetically by name")
        void sortedByName() {
            when(productRepository.findAll()).thenReturn(List.of(productB, productA)); // reverse order input

            List<InventoryItem> result = reportService.getInventoryReport();

            assertThat(result.get(0).name()).isEqualTo("Camiseta");
            assertThat(result.get(1).name()).isEqualTo("Pantalón");
        }

        @Test
        @DisplayName("maps all fields correctly from Product entity")
        void mapsFieldsCorrectly() {
            when(productRepository.findAll()).thenReturn(List.of(productA));

            List<InventoryItem> result = reportService.getInventoryReport();

            InventoryItem item = result.get(0);
            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.name()).isEqualTo("Camiseta");
            assertThat(item.sku()).isEqualTo("SKU-1");
            assertThat(item.stock()).isEqualTo(50);
            assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(29.99));
            assertThat(item.active()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no products exist")
        void emptyWhenNoProducts() {
            when(productRepository.findAll()).thenReturn(List.of());

            List<InventoryItem> result = reportService.getInventoryReport();

            assertThat(result).isEmpty();
        }
    }

    // ─── getOutOfStockProducts ────────────────────────────────────────────────

    @Nested
    @DisplayName("getOutOfStockProducts")
    class GetOutOfStockProducts {

        @Test
        @DisplayName("returns only products with stock == 0")
        void filtersToZeroStockOnly() {
            Product zeroStock = Product.builder()
                    .id(3L).name("Agotado").sku("SKU-3")
                    .price(BigDecimal.TEN).stockQuantity(0).active(true).build();
            when(productRepository.findAll()).thenReturn(List.of(productA, zeroStock));

            List<InventoryItem> result = reportService.getOutOfStockProducts();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Agotado");
            assertThat(result.get(0).stock()).isZero();
        }

        @Test
        @DisplayName("returns empty list when all products have stock > 0")
        void emptyWhenAllHaveStock() {
            when(productRepository.findAll()).thenReturn(List.of(productA, productB));

            List<InventoryItem> result = reportService.getOutOfStockProducts();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no products exist")
        void emptyWhenNoProducts() {
            when(productRepository.findAll()).thenReturn(List.of());

            List<InventoryItem> result = reportService.getOutOfStockProducts();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("sorts zero-stock products alphabetically by name")
        void sortedByName() {
            Product zero1 = Product.builder().id(3L).name("Zapatos")
                    .price(BigDecimal.TEN).stockQuantity(0).active(true).build();
            Product zero2 = Product.builder().id(4L).name("Aretes")
                    .price(BigDecimal.TEN).stockQuantity(0).active(true).build();
            when(productRepository.findAll()).thenReturn(List.of(zero1, zero2));

            List<InventoryItem> result = reportService.getOutOfStockProducts();

            assertThat(result.get(0).name()).isEqualTo("Aretes");
            assertThat(result.get(1).name()).isEqualTo("Zapatos");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Order buildOrder(Long id, OrderStatus status, BigDecimal total) {
        return Order.builder()
                .id(id)
                .orderNumber("ORD-" + id)
                .status(status)
                .totalAmount(total)
                .createdAt(LocalDateTime.now())
                .shippingAddress("Calle 1").shippingCity("CDMX")
                .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                .build();
    }
}
