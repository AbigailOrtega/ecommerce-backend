package com.ecommerce.service;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.*;
import com.ecommerce.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSizeRepository productSizeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private ShippingConfigService shippingConfigService;
    @Mock private GoogleMapsService googleMapsService;
    @Mock private PickupLocationService pickupLocationService;
    @Mock private PickupTimeSlotRepository pickupTimeSlotRepository;
    @Mock private PromotionRepository promotionRepository;

    @InjectMocks private OrderService orderService;

    private User customer;
    private Product product;
    private CartItem cartItem;
    private ShippingConfig freeShippingConfig;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(1L).firstName("Ana").lastName("López")
                .email("ana@example.com").role(Role.CUSTOMER).enabled(true)
                .build();

        product = Product.builder()
                .id(10L).name("Camiseta").price(BigDecimal.valueOf(29.99))
                .stockQuantity(50).active(true).build();

        cartItem = CartItem.builder()
                .id(100L).product(product).quantity(2)
                .build();

        freeShippingConfig = ShippingConfig.builder()
                .id(1L)
                .nationalEnabled(true)
                .pickupEnabled(true)
                .nationalBasePrice(BigDecimal.ZERO)
                .nationalPricePerKm(BigDecimal.ZERO)
                .originAddress("Tienda Centro")
                .googleMapsApiKey("test-key")
                .pickupCost(BigDecimal.ZERO)
                .build();
    }

    private OrderRequest sampleRequest() {
        return new OrderRequest("Calle 1", "CDMX", "CMX", "06600", "MX",
                "CARD", null, null, null, "NATIONAL", null, null, null);
    }

    // ─── createOrder ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("creates order and clears cart on success")
        void createOrder_success() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));
            when(cartItemRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
            when(shippingConfigService.getOrCreate()).thenReturn(freeShippingConfig);
            when(googleMapsService.calculateDistanceKm(anyString(), anyString(), anyString()))
                    .thenReturn(0.0);
            when(promotionRepository.findBestActivePromotion(any(), any())).thenReturn(java.util.Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(200L);
                return o;
            });
            doNothing().when(emailService).sendOrderConfirmationEmail(any(), any());

            OrderResponse response = orderService.createOrder("ana@example.com", sampleRequest());

            assertThat(response).isNotNull();
            verify(cartItemRepository).deleteByUserId(1L);
            verify(emailService).sendOrderConfirmationEmail(eq(customer), any());
        }

        @Test
        @DisplayName("throws BadRequestException when cart is empty")
        void createOrder_emptyCart() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));
            when(cartItemRepository.findByUserId(1L)).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder("ana@example.com", sampleRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cart is empty");
            verify(orderRepository, never()).save(any());
        }
    }

    // ─── getUserOrders ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserOrders")
    class GetUserOrders {

        @Test
        @DisplayName("returns list of orders for the user")
        void getUserOrders_success() {
            Order order = Order.builder()
                    .id(1L).orderNumber("ORD-ABCD1234").user(customer)
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.valueOf(59.98)).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(order));

            List<OrderResponse> orders = orderService.getUserOrders("ana@example.com");

            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getOrderNumber()).isEqualTo("ORD-ABCD1234");
        }

        @Test
        @DisplayName("returns empty list when user has no orders")
        void getUserOrders_empty() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

            List<OrderResponse> orders = orderService.getUserOrders("ana@example.com");

            assertThat(orders).isEmpty();
        }
    }

    // ─── getOrderByNumber ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderByNumber")
    class GetOrderByNumber {

        @Test
        @DisplayName("returns order when it belongs to the requesting user")
        void getOrderByNumber_success() {
            Order order = Order.builder()
                    .id(1L).orderNumber("ORD-ABCD1234").user(customer)
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN).build();

            when(orderRepository.findByOrderNumber("ORD-ABCD1234")).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));

            OrderResponse response = orderService.getOrderByNumber("ORD-ABCD1234", "ana@example.com");

            assertThat(response.getOrderNumber()).isEqualTo("ORD-ABCD1234");
        }

        @Test
        @DisplayName("throws BadRequestException when order belongs to another user")
        void getOrderByNumber_unauthorized() {
            User otherUser = User.builder().id(99L).email("other@example.com")
                    .role(Role.CUSTOMER).enabled(true).build();
            Order order = Order.builder()
                    .id(1L).orderNumber("ORD-XYZ").user(otherUser)
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN).build();

            when(orderRepository.findByOrderNumber("ORD-XYZ")).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> orderService.getOrderByNumber("ORD-XYZ", "ana@example.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Order does not belong to user");
        }
    }
}
