package com.ecommerce.service;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductColor;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService")
class AnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AnalyticsService analyticsService;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .firstName("Ana")
                .lastName("López")
                .email("ana@example.com")
                .phone("555-0100")
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();

        testOrder = Order.builder()
                .id(10L)
                .orderNumber("ORD-ABCD1234")
                .user(testUser)
                .totalAmount(new BigDecimal("199.99"))
                .status(OrderStatus.PENDING)
                .paymentMethod("CARD")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── getDashboardStats ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDashboardStats")
    class GetDashboardStats {

        @Test
        @DisplayName("returns correct aggregate counts from all repositories")
        void getDashboardStats_returnsCorrectCounts() {
            when(orderRepository.count()).thenReturn(42L);
            when(productRepository.count()).thenReturn(15L);
            when(userRepository.count()).thenReturn(200L);
            when(orderRepository.sumTotalRevenue()).thenReturn(new BigDecimal("9999.99"));
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getTotalOrders()).isEqualTo(42L);
            assertThat(result.getTotalProducts()).isEqualTo(15L);
            assertThat(result.getTotalUsers()).isEqualTo(200L);
            assertThat(result.getTotalRevenue()).isEqualByComparingTo("9999.99");
        }

        @Test
        @DisplayName("requests only the 5 most recent orders for the recent-orders list")
        void getDashboardStats_requestsFiveMostRecentOrders() {
            when(orderRepository.count()).thenReturn(0L);
            when(productRepository.count()).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            analyticsService.getDashboardStats();

            verify(orderRepository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
        }

        @Test
        @DisplayName("ordersByStatus map contains an entry for every OrderStatus value")
        void getDashboardStats_ordersByStatusContainsAllStatuses() {
            when(orderRepository.count()).thenReturn(0L);
            when(productRepository.count()).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getOrdersByStatus()).containsKeys(
                    Arrays.stream(OrderStatus.values())
                            .map(Enum::name)
                            .toArray(String[]::new)
            );
            assertThat(result.getOrdersByStatus()).hasSize(OrderStatus.values().length);
        }

        @Test
        @DisplayName("recentOrders list is empty when repository page is empty")
        void getDashboardStats_emptyRecentOrdersWhenNoneExist() {
            when(orderRepository.count()).thenReturn(0L);
            when(productRepository.count()).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getRecentOrders()).isEmpty();
        }

        @Test
        @DisplayName("maps a single recent order to OrderResponse with correct fields")
        void getDashboardStats_mapsRecentOrderCorrectly() {
            // Order with no items and no selectedSize
            OrderItem item = OrderItem.builder()
                    .id(100L)
                    .product(null)
                    .productName("Sneaker")
                    .productPrice(new BigDecimal("99.99"))
                    .quantity(2)
                    .subtotal(new BigDecimal("199.98"))
                    .selectedSize(null)
                    .build();
            testOrder.getItems().add(item);

            when(orderRepository.count()).thenReturn(1L);
            when(productRepository.count()).thenReturn(5L);
            when(userRepository.count()).thenReturn(10L);
            when(orderRepository.sumTotalRevenue()).thenReturn(new BigDecimal("199.99"));
            stubCountByStatusForAllStatuses();

            Page<Order> page = new PageImpl<>(List.of(testOrder));
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getRecentOrders()).hasSize(1);
            var orderResponse = result.getRecentOrders().get(0);
            assertThat(orderResponse.getId()).isEqualTo(10L);
            assertThat(orderResponse.getOrderNumber()).isEqualTo("ORD-ABCD1234");
            assertThat(orderResponse.getStatus()).isEqualTo("PENDING");
            assertThat(orderResponse.getPaymentMethod()).isEqualTo("CARD");
            assertThat(orderResponse.getTotalAmount()).isEqualByComparingTo("199.99");
        }

        @Test
        @DisplayName("maps user fields into the nested UserResponse on each OrderResponse")
        void getDashboardStats_mapsUserFieldsCorrectly() {
            Page<Order> page = new PageImpl<>(List.of(testOrder));

            when(orderRepository.count()).thenReturn(1L);
            when(productRepository.count()).thenReturn(1L);
            when(userRepository.count()).thenReturn(1L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            var user = result.getRecentOrders().get(0).getUser();
            assertThat(user.id()).isEqualTo(1L);
            assertThat(user.firstName()).isEqualTo("Ana");
            assertThat(user.lastName()).isEqualTo("López");
            assertThat(user.email()).isEqualTo("ana@example.com");
            assertThat(user.role()).isEqualTo("CUSTOMER");
        }

        @Test
        @DisplayName("maps order item with selectedSize to correct color and size names")
        void getDashboardStats_mapsOrderItemWithSelectedSizeCorrectly() {
            ProductColor color = ProductColor.builder().id(1L).name("Red").build();
            ProductSize size = ProductSize.builder().id(1L).name("M").color(color).build();
            Product product = Product.builder().id(50L).name("T-Shirt").price(new BigDecimal("29.99")).build();

            OrderItem item = OrderItem.builder()
                    .id(200L)
                    .product(product)
                    .productName("T-Shirt")
                    .productPrice(new BigDecimal("29.99"))
                    .quantity(1)
                    .subtotal(new BigDecimal("29.99"))
                    .selectedSize(size)
                    .build();
            testOrder.getItems().add(item);

            Page<Order> page = new PageImpl<>(List.of(testOrder));
            when(orderRepository.count()).thenReturn(1L);
            when(productRepository.count()).thenReturn(1L);
            when(userRepository.count()).thenReturn(1L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            var itemResponse = result.getRecentOrders().get(0).getItems().get(0);
            assertThat(itemResponse.productId()).isEqualTo(50L);
            assertThat(itemResponse.selectedColorName()).isEqualTo("Red");
            assertThat(itemResponse.selectedSizeName()).isEqualTo("M");
        }

        @Test
        @DisplayName("maps order item with null product to null productId")
        void getDashboardStats_mapsOrderItemWithNullProductToNullProductId() {
            OrderItem item = OrderItem.builder()
                    .id(300L)
                    .product(null)
                    .productName("Deleted Product")
                    .productPrice(new BigDecimal("10.00"))
                    .quantity(1)
                    .subtotal(new BigDecimal("10.00"))
                    .selectedSize(null)
                    .build();
            testOrder.getItems().add(item);

            Page<Order> page = new PageImpl<>(List.of(testOrder));
            when(orderRepository.count()).thenReturn(1L);
            when(productRepository.count()).thenReturn(1L);
            when(userRepository.count()).thenReturn(1L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.ZERO);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            var itemResponse = result.getRecentOrders().get(0).getItems().get(0);
            assertThat(itemResponse.productId()).isNull();
            assertThat(itemResponse.selectedColorName()).isNull();
            assertThat(itemResponse.selectedSizeName()).isNull();
        }

        @Test
        @DisplayName("totalRevenue is null when repository returns null (no non-cancelled orders)")
        void getDashboardStats_handlesNullTotalRevenue() {
            when(orderRepository.count()).thenReturn(0L);
            when(productRepository.count()).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(orderRepository.sumTotalRevenue()).thenReturn(null);
            stubCountByStatusForAllStatuses();
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getTotalRevenue()).isNull();
        }

        @Test
        @DisplayName("ordersByStatus values reflect counts returned by repository per status")
        void getDashboardStats_ordersByStatusValuesMatchRepositoryCounts() {
            when(orderRepository.count()).thenReturn(10L);
            when(productRepository.count()).thenReturn(5L);
            when(userRepository.count()).thenReturn(3L);
            when(orderRepository.sumTotalRevenue()).thenReturn(BigDecimal.TEN);
            when(orderRepository.countByStatus(eq(OrderStatus.PENDING))).thenReturn(3L);
            when(orderRepository.countByStatus(eq(OrderStatus.CONFIRMED))).thenReturn(2L);
            when(orderRepository.countByStatus(eq(OrderStatus.PROCESSING))).thenReturn(1L);
            when(orderRepository.countByStatus(eq(OrderStatus.SHIPPED))).thenReturn(1L);
            when(orderRepository.countByStatus(eq(OrderStatus.DELIVERED))).thenReturn(2L);
            when(orderRepository.countByStatus(eq(OrderStatus.CANCELLED))).thenReturn(1L);
            when(orderRepository.countByStatus(eq(OrderStatus.REFUNDED))).thenReturn(0L);
            when(orderRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            DashboardStatsResponse result = analyticsService.getDashboardStats();

            assertThat(result.getOrdersByStatus().get("PENDING")).isEqualTo(3L);
            assertThat(result.getOrdersByStatus().get("CONFIRMED")).isEqualTo(2L);
            assertThat(result.getOrdersByStatus().get("DELIVERED")).isEqualTo(2L);
            assertThat(result.getOrdersByStatus().get("CANCELLED")).isEqualTo(1L);
            assertThat(result.getOrdersByStatus().get("REFUNDED")).isEqualTo(0L);
        }

        // ── helper ────────────────────────────────────────────────────────────

        private void stubCountByStatusForAllStatuses() {
            for (OrderStatus status : OrderStatus.values()) {
                when(orderRepository.countByStatus(eq(status))).thenReturn(0L);
            }
        }
    }
}
