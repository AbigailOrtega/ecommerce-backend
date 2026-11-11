package com.ecommerce.service;

import com.ecommerce.dto.response.AccountingReportResponse;
import com.ecommerce.dto.response.InventoryItem;
import com.ecommerce.dto.response.ProductSalesItem;
import com.ecommerce.dto.response.SalesReportResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
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
import java.time.LocalDate;
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
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("week");

            assertThat(result.period()).isEqualTo("Última semana");
        }

        @Test
        @DisplayName("returns 'Este año' label for period=year")
        void year_returnsCorrectPeriodLabel() {
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("year");

            assertThat(result.period()).isEqualTo("Este año");
        }

        @Test
        @DisplayName("defaults to 'Este mes' for unknown period")
        void unknown_defaultsToMonth() {
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("unknown");

            assertThat(result.period()).isEqualTo("Este mes");
        }

        @Test
        @DisplayName("excludes CANCELLED orders from revenue and order count")
        void excludesCancelledOrders() {
            Order active = buildOrder(1L, OrderStatus.CONFIRMED, BigDecimal.valueOf(100));
            Order cancelled = buildOrder(2L, OrderStatus.CANCELLED, BigDecimal.valueOf(50));
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any()))
                    .thenReturn(List.of(active, cancelled));

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(result.totalOrders()).isEqualTo(1);
        }

        @Test
        @DisplayName("excludes REFUNDED orders from revenue")
        void excludesRefundedOrders() {
            Order refunded = buildOrder(1L, OrderStatus.REFUNDED, BigDecimal.valueOf(80));
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any()))
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
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any()))
                    .thenReturn(List.of(o1, o2));

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).orderCount()).isEqualTo(2);
            assertThat(result.data().get(0).revenue()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        @DisplayName("returns zero totals and empty data when no orders")
        void emptyOrders_returnsZeroTotals() {
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport("month");

            assertThat(result.data()).isEmpty();
            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalOrders()).isZero();
        }

        @Test
        @DisplayName("custom dates: queries from the first day's start to the last day's end, labelled with both days")
        void customRange_queriesWholeDays() {
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            SalesReportResponse result = reportService.getSalesReport(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20));

            org.mockito.ArgumentCaptor<LocalDateTime> start = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
            org.mockito.ArgumentCaptor<LocalDateTime> end = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
            org.mockito.Mockito.verify(orderRepository).findByPaidAtBetweenOrderByPaidAtAsc(start.capture(), end.capture());
            assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 3, 0, 0));
            assertThat(end.getValue()).isAfter(LocalDateTime.of(2026, 10, 20, 23, 59, 59))
                    .isBefore(LocalDateTime.of(2026, 10, 21, 0, 0));
            assertThat(result.period()).isEqualTo("Del 03/10/2026 al 20/10/2026");
        }

        @Test
        @DisplayName("custom dates: a single day is labelled with that day")
        void customRange_singleDay() {
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of());

            assertThat(reportService.getSalesReport(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)).period())
                    .isEqualTo("05/11/2026");
        }

        @Test
        @DisplayName("custom dates: rejects an end before the start, and missing dates")
        void customRange_invalid() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> reportService.getSalesReport(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)))
                    .isInstanceOf(com.ecommerce.exception.BadRequestException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> reportService.getSalesReport(LocalDate.of(2026, 11, 5), null))
                    .isInstanceOf(com.ecommerce.exception.BadRequestException.class);
        }
    }

    // ─── getAccountingReport ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountingReport")
    class Accounting {

        private final LocalDate from = LocalDate.of(2026, 10, 1);
        private final LocalDate to = LocalDate.of(2026, 10, 31);

        private Order paidOrder(String number, String method, String items, String discount, String shipping,
                                String fee, String label, Integer qty, String unitCost) {
            BigDecimal itemTotal = new BigDecimal(items);
            Order o = Order.builder().orderNumber(number).status(OrderStatus.DELIVERED).paymentMethod(method)
                    .paymentId("pay-" + number).guestFirstName("Ana").guestLastName("Ruiz").guestEmail("ana@example.com")
                    .discountAmount(new BigDecimal(discount)).shippingCost(new BigDecimal(shipping))
                    .totalAmount(itemTotal.subtract(new BigDecimal(discount)).add(new BigDecimal(shipping)))
                    .paymentFee(fee == null ? null : new BigDecimal(fee))
                    .shippingLabelCost(label == null ? null : new BigDecimal(label))
                    .shippingType(label == null ? "PICKUP" : "NATIONAL")
                    .paidAt(LocalDateTime.of(2026, 10, 5, 12, 0))
                    .shippingAddress("x").shippingCity("x").shippingState("x").shippingZipCode("x").shippingCountry("MX")
                    .build();
            o.addItem(OrderItem.builder().productName("Vestido").productPrice(itemTotal.divide(BigDecimal.valueOf(qty)))
                    .quantity(qty).subtotal(itemTotal).unitCost(unitCost == null ? null : new BigDecimal(unitCost)).build());
            return o;
        }

        @Test
        @DisplayName("breaks the money down, subtracts refunds and costs, and lists every movement")
        void breakdown() {
            // card: 2 dresses of 500, coupon 100, shipping 150 → 1050; commission 40.80, label 120, cost 200 each
            Order card = paidOrder("ORD-A", "stripe", "1000", "100", "150", "40.80", "120", 2, "200");
            // PayPal pickup: 300, commission not known yet, no cost recorded
            Order paypal = paidOrder("ORD-B", "paypal", "300", "0", "0", null, null, 1, null);
            // paid in September, refunded on Oct 20: the refund belongs to October, the sale does not
            Order refunded = paidOrder("ORD-C", "stripe", "400", "0", "0", "17.40", null, 1, "150");
            refunded.setPaidAt(LocalDateTime.of(2026, 9, 28, 10, 0));
            refunded.setStatus(OrderStatus.REFUNDED);
            refunded.setRefundedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of(card, paypal));
            when(orderRepository.findByRefundedAtBetweenOrderByRefundedAtAsc(any(), any())).thenReturn(List.of(refunded));

            AccountingReportResponse r = reportService.getAccountingReport(from, to);
            AccountingReportResponse.Summary s = r.summary();

            assertThat(s.paidOrders()).isEqualTo(2);
            assertThat(s.productSales()).isEqualByComparingTo("1300");
            assertThat(s.discounts()).isEqualByComparingTo("100");
            assertThat(s.shippingCharged()).isEqualByComparingTo("150");
            assertThat(s.totalCollected()).isEqualByComparingTo("1350");
            assertThat(s.refundedOrders()).isEqualTo(1);
            assertThat(s.refunds()).isEqualByComparingTo("400");
            assertThat(s.netSales()).isEqualByComparingTo("950");
            assertThat(s.paymentFees()).isEqualByComparingTo("40.80");
            assertThat(s.ordersMissingFee()).isEqualTo(1);
            assertThat(s.labelCosts()).isEqualByComparingTo("120");
            assertThat(s.shipmentsMissingCost()).isZero();
            // 2 × 200 sold, 150 came back with the refund
            assertThat(s.costOfGoods()).isEqualByComparingTo("250");
            assertThat(s.unitsMissingCost()).isEqualTo(1);
            // 950 - 40.80 - 120 - 250
            assertThat(s.grossProfit()).isEqualByComparingTo("539.20");

            assertThat(r.byPaymentMethod()).extracting(AccountingReportResponse.PaymentMethodTotal::method).containsExactly("paypal", "stripe");
            assertThat(r.byPaymentMethod().get(1).collected()).isEqualByComparingTo("1050");
            assertThat(r.daily()).hasSize(1);
            assertThat(r.daily().get(0).revenue()).isEqualByComparingTo("1350");

            assertThat(r.ledger()).extracting(AccountingReportResponse.LedgerRow::movement).containsExactly("VENTA", "VENTA", "REEMBOLSO");
            AccountingReportResponse.LedgerRow refund = r.ledger().get(2);
            assertThat(refund.orderNumber()).isEqualTo("ORD-C");
            assertThat(refund.total()).isEqualByComparingTo("-400");
            assertThat(refund.customer()).isEqualTo("Ana Ruiz");
        }

        @Test
        @DisplayName("a paid order cancelled without a refund is flagged; a national order without label cost too")
        void warnings() {
            Order cancelled = paidOrder("ORD-D", "stripe", "200", "0", "0", "10.20", null, 1, "50");
            cancelled.setStatus(OrderStatus.CANCELLED);
            Order national = paidOrder("ORD-E", "transfer", "200", "0", "100", null, "1", 1, "50");
            national.setShippingLabelCost(null);
            when(orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(any(), any())).thenReturn(List.of(cancelled, national));
            when(orderRepository.findByRefundedAtBetweenOrderByRefundedAtAsc(any(), any())).thenReturn(List.of());

            AccountingReportResponse.Summary s = reportService.getAccountingReport(from, to).summary();

            assertThat(s.paidThenCancelled()).isEqualTo(1);
            assertThat(s.shipmentsMissingCost()).isEqualTo(1);
            assertThat(s.ordersMissingFee()).as("a transfer has no commission to look up").isZero();
        }

        @Test
        @DisplayName("rejects a range that is backwards or too long")
        void invalidRange() {
            assertThatThrownBy(() -> reportService.getAccountingReport(to, from)).hasMessageContaining("anterior");
            assertThatThrownBy(() -> reportService.getAccountingReport(from, from.plusDays(400))).hasMessageContaining("400");
            assertThatThrownBy(() -> reportService.getAccountingReport(null, to)).hasMessageContaining("fechas");
        }

        @Test
        @DisplayName("setting a product's cost fills past sales that had none")
        void setCost() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));
            when(orderItemRepository.fillMissingUnitCost(1L, new BigDecimal("12.50"))).thenReturn(3);

            assertThat(reportService.setProductCost(1L, new BigDecimal("12.5"))).isEqualTo(3);
            assertThat(productA.getCostPrice()).isEqualByComparingTo("12.50");
            verify(productRepository).save(productA);

            assertThat(reportService.setProductCost(1L, null)).isZero();
            assertThat(productA.getCostPrice()).isNull();
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
        @DisplayName("each row carries the product's slug, for the link to its store page")
        void carriesSlug() {
            productA.setSlug("camiseta");
            List<Object[]> rows = List.<Object[]>of(new Object[]{1L, "Camiseta", 3L, BigDecimal.TEN});
            when(orderItemRepository.findProductSalesRanked(any())).thenReturn(rows);
            when(productRepository.findById(1L)).thenReturn(Optional.of(productA));

            assertThat(reportService.getTopSellingProducts(20).get(0).slug()).isEqualTo("camiseta");
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
        @DisplayName("each row carries the product's slug, for the link to its store page")
        void carriesSlug() {
            productA.setSlug("camiseta");
            when(productRepository.findAll()).thenReturn(List.of(productA));

            assertThat(reportService.getInventoryReport().get(0).slug()).isEqualTo("camiseta");
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
                .paidAt(LocalDateTime.now())
                .shippingAddress("Calle 1").shippingCity("CDMX")
                .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                .build();
    }
}
