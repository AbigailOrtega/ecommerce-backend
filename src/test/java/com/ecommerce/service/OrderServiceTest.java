package com.ecommerce.service;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.UpcomingScheduleResponse;
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
    @Mock private SkydropxService skydropxService;

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

    private Order buildShippingOrder(String shippingType, OrderStatus status, String locationName) {
        return Order.builder()
                .id(System.nanoTime()).orderNumber("ORD-" + System.nanoTime())
                .user(customer).status(status)
                .totalAmount(BigDecimal.valueOf(100))
                .shippingType(shippingType)
                .pickupLocationName(locationName)
                .shippingAddress("Calle 1").shippingCity("CDMX")
                .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                .build();
    }

    private OrderRequest sampleRequest() {
        return new OrderRequest("Calle 1", "CDMX", "CMX", "06600", "MX",
                "CARD", null, null, null, "NATIONAL", null, null, null, null);
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

    // ─── updateOrderStatus — guest email ─────────────────────────────────────

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatus {

        @Test
        @DisplayName("sends guest status email when order has no user but has guestEmail")
        void guestOrder_sendsGuestStatusEmail() {
            Order guestOrder = Order.builder()
                    .id(5L).orderNumber("ORD-GUEST01")
                    .guestEmail("guest@example.com").guestFirstName("Invitado")
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.valueOf(100))
                    .shippingAddress("Calle 1").shippingCity("CDMX")
                    .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                    .build();

            when(orderRepository.findById(5L)).thenReturn(Optional.of(guestOrder));
            when(orderRepository.save(any())).thenReturn(guestOrder);

            orderService.updateOrderStatus(5L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

            verify(emailService).sendGuestOrderStatusUpdateEmail(
                    eq("guest@example.com"), eq("Invitado"), any());
            verify(emailService, never()).sendOrderStatusUpdateEmail(any(), any());
        }

        @Test
        @DisplayName("falls back to 'Cliente' when guestFirstName is null")
        void guestOrder_usesClienteFallbackWhenNoFirstName() {
            Order guestOrder = Order.builder()
                    .id(6L).orderNumber("ORD-GUEST02")
                    .guestEmail("noname@example.com").guestFirstName(null)
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.valueOf(50))
                    .shippingAddress("Calle 1").shippingCity("CDMX")
                    .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                    .build();

            when(orderRepository.findById(6L)).thenReturn(Optional.of(guestOrder));
            when(orderRepository.save(any())).thenReturn(guestOrder);

            orderService.updateOrderStatus(6L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

            verify(emailService).sendGuestOrderStatusUpdateEmail(
                    eq("noname@example.com"), eq("Cliente"), any());
        }

        @Test
        @DisplayName("sends registered-user status email when order has a user")
        void registeredOrder_sendsUserStatusEmail() {
            Order userOrder = Order.builder()
                    .id(7L).orderNumber("ORD-USER01").user(customer)
                    .status(OrderStatus.PENDING).totalAmount(BigDecimal.valueOf(80))
                    .shippingAddress("Calle 1").shippingCity("CDMX")
                    .shippingState("CMX").shippingZipCode("06600").shippingCountry("MX")
                    .build();

            when(orderRepository.findById(7L)).thenReturn(Optional.of(userOrder));
            when(orderRepository.save(any())).thenReturn(userOrder);

            orderService.updateOrderStatus(7L, new UpdateOrderStatusRequest(OrderStatus.SHIPPED));

            verify(emailService).sendOrderStatusUpdateEmail(eq(customer), any());
            verify(emailService, never()).sendGuestOrderStatusUpdateEmail(any(), any(), any());
        }
    }

    // ─── getUpcomingSchedule ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getUpcomingSchedule")
    class GetUpcomingSchedule {

        @Test
        @DisplayName("returns NATIONAL shipments in confirmed/processing state")
        void returnsNationalShipments() {
            Order national = buildShippingOrder("NATIONAL", OrderStatus.CONFIRMED, null);
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("NATIONAL"), any())).thenReturn(List.of(national));
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("PICKUP"), any())).thenReturn(List.of());

            UpcomingScheduleResponse result = orderService.getUpcomingSchedule();

            assertThat(result.shipments()).hasSize(1);
            assertThat(result.pickups()).isEmpty();
        }

        @Test
        @DisplayName("groups PICKUP orders by pickup location name")
        void groupsPickupsByLocation() {
            Order pickup1 = buildShippingOrder("PICKUP", OrderStatus.CONFIRMED, "Sucursal Norte");
            Order pickup2 = buildShippingOrder("PICKUP", OrderStatus.PROCESSING, "Sucursal Sur");
            Order pickup3 = buildShippingOrder("PICKUP", OrderStatus.CONFIRMED, "Sucursal Norte");
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("NATIONAL"), any())).thenReturn(List.of());
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("PICKUP"), any())).thenReturn(List.of(pickup1, pickup2, pickup3));

            UpcomingScheduleResponse result = orderService.getUpcomingSchedule();

            assertThat(result.pickups()).hasSize(2);
            UpcomingScheduleResponse.PickupGroupResponse norte = result.pickups().stream()
                    .filter(g -> g.locationName().equals("Sucursal Norte")).findFirst().orElseThrow();
            assertThat(norte.orders()).hasSize(2);
        }

        @Test
        @DisplayName("uses 'Sin ubicación' when pickupLocationName is null")
        void usesDefaultLocationNameWhenNull() {
            Order pickup = buildShippingOrder("PICKUP", OrderStatus.CONFIRMED, null);
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("NATIONAL"), any())).thenReturn(List.of());
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("PICKUP"), any())).thenReturn(List.of(pickup));

            UpcomingScheduleResponse result = orderService.getUpcomingSchedule();

            assertThat(result.pickups().get(0).locationName()).isEqualTo("Sin ubicación");
        }

        @Test
        @DisplayName("returns empty shipments and pickups when no active orders exist")
        void emptyWhenNoActiveOrders() {
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("NATIONAL"), any())).thenReturn(List.of());
            when(orderRepository.findByShippingTypeAndStatusInOrderByCreatedAtAsc(
                    eq("PICKUP"), any())).thenReturn(List.of());

            UpcomingScheduleResponse result = orderService.getUpcomingSchedule();

            assertThat(result.shipments()).isEmpty();
            assertThat(result.pickups()).isEmpty();
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
