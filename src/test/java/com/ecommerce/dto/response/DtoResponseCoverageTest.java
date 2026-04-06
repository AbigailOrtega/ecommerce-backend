package com.ecommerce.dto.response;

import com.ecommerce.entity.AvailabilityType;
import com.ecommerce.entity.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("dto.response coverage")
class DtoResponseCoverageTest {

    // ── ApiResponse ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ApiResponse")
    class ApiResponseTests {

        @Test
        void successWithData() {
            ApiResponse<String> r = ApiResponse.success("hello");
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isEqualTo("hello");
            assertThat(r.getMessage()).isNull();
        }

        @Test
        void successWithMessageAndData() {
            ApiResponse<Integer> r = ApiResponse.success("ok", 42);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getMessage()).isEqualTo("ok");
            assertThat(r.getData()).isEqualTo(42);
        }

        @Test
        void error() {
            ApiResponse<Void> r = ApiResponse.error("algo salió mal");
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getMessage()).isEqualTo("algo salió mal");
            assertThat(r.getData()).isNull();
        }

        @Test
        void builderAndSetters() {
            ApiResponse<String> r = new ApiResponse<>();
            r.setSuccess(true);
            r.setMessage("msg");
            r.setData("data");
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getMessage()).isEqualTo("msg");
            assertThat(r.getData()).isEqualTo("data");
        }
    }

    // ── AuthResponse ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AuthResponse")
    class AuthResponseTests {

        @Test
        void defaultTokenType() {
            AuthResponse r = AuthResponse.builder()
                    .accessToken("acc")
                    .refreshToken("ref")
                    .build();
            assertThat(r.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        void allArgsConstructor() {
            UserResponse user = new UserResponse(1L, "Ana", "G", "a@b.com", "555", "CUSTOMER");
            AuthResponse r = new AuthResponse("acc", "ref", "Bearer", user);
            assertThat(r.getUser()).isEqualTo(user);
        }
    }

    // ── DashboardStatsResponse ────────────────────────────────────────────────

    @Test
    @DisplayName("DashboardStatsResponse builder y getters")
    void dashboardStats() {
        DashboardStatsResponse r = DashboardStatsResponse.builder()
                .totalOrders(10L)
                .totalProducts(5L)
                .totalUsers(3L)
                .totalRevenue(BigDecimal.valueOf(1500))
                .recentOrders(List.of())
                .ordersByStatus(Map.of("PENDING", 2L))
                .build();
        assertThat(r.getTotalOrders()).isEqualTo(10L);
        assertThat(r.getTotalRevenue()).isEqualByComparingTo("1500");
        assertThat(r.getOrdersByStatus()).containsKey("PENDING");
    }

    // ── OrderResponse ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("OrderResponse builder y getters")
    void orderResponse() {
        OrderResponse r = OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .status("PENDING")
                .totalAmount(BigDecimal.valueOf(200))
                .pickupDate(LocalDate.of(2026, 4, 1))
                .pickupCancelled(false)
                .build();
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(r.getPickupDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.isPickupCancelled()).isFalse();
    }

    // ── ReviewResponse ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ReviewResponse builder y getters")
    void reviewResponse() {
        LocalDateTime now = LocalDateTime.now();
        ReviewResponse r = ReviewResponse.builder()
                .id(1L)
                .productId(2L)
                .userId(3L)
                .userName("Ana")
                .rating(5)
                .title("Excelente")
                .comment("Muy bueno")
                .verified(true)
                .approved(true)
                .productName("Camiseta")
                .createdAt(now)
                .build();
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getRating()).isEqualTo(5);
        assertThat(r.isVerified()).isTrue();
        assertThat(r.getCreatedAt()).isEqualTo(now);

        r.setApproved(false);
        assertThat(r.isApproved()).isFalse();
    }

    // ── ReviewSummaryResponse ─────────────────────────────────────────────────

    @Test
    @DisplayName("ReviewSummaryResponse builder y getters")
    void reviewSummary() {
        ReviewSummaryResponse r = ReviewSummaryResponse.builder()
                .averageRating(4.5)
                .totalReviews(20L)
                .ratingDistribution(Map.of(5, 10L, 4, 10L))
                .build();
        assertThat(r.getAverageRating()).isEqualTo(4.5);
        assertThat(r.getTotalReviews()).isEqualTo(20L);
        assertThat(r.getRatingDistribution()).containsKey(5);
    }

    // ── Records simples ───────────────────────────────────────────────────────

    @Test @DisplayName("UserResponse record") void userResponse() {
        UserResponse r = new UserResponse(1L, "Ana", "García", "a@b.com", "555", "CUSTOMER");
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.email()).isEqualTo("a@b.com");
        assertThat(r.role()).isEqualTo("CUSTOMER");
    }

    @Test @DisplayName("CategoryResponse record") void categoryResponse() {
        CategoryResponse r = new CategoryResponse(1L, "Ropa", "desc", "img.jpg", "ropa");
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.slug()).isEqualTo("ropa");
    }

    @Test @DisplayName("ProductSizeResponse record") void productSizeResponse() {
        ProductSizeResponse r = new ProductSizeResponse(1L, "M", 10);
        assertThat(r.name()).isEqualTo("M");
        assertThat(r.stock()).isEqualTo(10);
    }

    @Test @DisplayName("ProductColorResponse record") void productColorResponse() {
        ProductColorResponse r = new ProductColorResponse(1L, "Rojo", List.of("img.jpg"), List.of());
        assertThat(r.name()).isEqualTo("Rojo");
        assertThat(r.sizes()).isEmpty();
    }

    @Test @DisplayName("ProductVariantResponse record") void productVariantResponse() {
        ProductVariantResponse r = new ProductVariantResponse(1L, "Azul", "L", 5);
        assertThat(r.color()).isEqualTo("Azul");
        assertThat(r.stock()).isEqualTo(5);
    }

    @Test @DisplayName("ProductResponse record") void productResponse() {
        LocalDateTime now = LocalDateTime.now();
        ProductResponse r = new ProductResponse(1L, "Camiseta", "desc",
                BigDecimal.valueOf(100), BigDecimal.valueOf(120), "SKU01", 50,
                "img.jpg", List.of("img.jpg"), "camiseta", true, true,
                List.of(), now, List.of(), BigDecimal.valueOf(90), BigDecimal.valueOf(10), "Promo");
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.featured()).isTrue();
        assertThat(r.activePromotionName()).isEqualTo("Promo");
    }

    @Test @DisplayName("OrderItemResponse record") void orderItemResponse() {
        OrderItemResponse r = new OrderItemResponse(1L, 2L, "Camiseta",
                BigDecimal.valueOf(100), 2, BigDecimal.valueOf(200), "Rojo", "M");
        assertThat(r.productName()).isEqualTo("Camiseta");
        assertThat(r.quantity()).isEqualTo(2);
    }

    @Test @DisplayName("CartItemResponse record") void cartItemResponse() {
        ProductResponse product = new ProductResponse(1L, "P", "", BigDecimal.ONE, null,
                "SKU", 1, "", List.of(), "p", false, true, List.of(), LocalDateTime.now(),
                List.of(), BigDecimal.ONE, null, null);
        CartItemResponse r = new CartItemResponse(1L, product, 2, BigDecimal.valueOf(200), "Rojo", "M");
        assertThat(r.quantity()).isEqualTo(2);
        assertThat(r.selectedColorName()).isEqualTo("Rojo");
    }

    @Test @DisplayName("CouponResponse record") void couponResponse() {
        LocalDateTime now = LocalDateTime.now();
        CouponResponse r = new CouponResponse(1L, "SAVE10", BigDecimal.valueOf(10),
                LocalDate.of(2026, 12, 31), true, 5, 100, now);
        assertThat(r.code()).isEqualTo("SAVE10");
        assertThat(r.active()).isTrue();
    }

    @Test @DisplayName("InventoryItem record") void inventoryItem() {
        InventoryItem r = new InventoryItem(1L, "Camiseta", "SKU01", 50, BigDecimal.valueOf(100), true, List.of());
        assertThat(r.name()).isEqualTo("Camiseta");
        assertThat(r.stock()).isEqualTo(50);
    }

    @Test @DisplayName("ProductSalesItem record") void productSalesItem() {
        ProductSalesItem r = new ProductSalesItem(1L, "Camiseta", 20L, BigDecimal.valueOf(2000), 30);
        assertThat(r.unitsSold()).isEqualTo(20L);
        assertThat(r.revenue()).isEqualByComparingTo("2000");
    }

    @Test @DisplayName("SalesDataPoint record") void salesDataPoint() {
        SalesDataPoint r = new SalesDataPoint("2026-01-01", 10L, BigDecimal.valueOf(500));
        assertThat(r.date()).isEqualTo("2026-01-01");
        assertThat(r.orderCount()).isEqualTo(10L);
    }

    @Test @DisplayName("SalesReportResponse record") void salesReportResponse() {
        SalesReportResponse r = new SalesReportResponse("MONTHLY", List.of(), BigDecimal.ZERO, 0L);
        assertThat(r.period()).isEqualTo("MONTHLY");
        assertThat(r.data()).isEmpty();
    }

    @Test @DisplayName("ShippingCalculateResponse record") void shippingCalculateResponse() {
        ShippingCalculateResponse r = new ShippingCalculateResponse(15.5, BigDecimal.valueOf(50));
        assertThat(r.distanceKm()).isEqualTo(15.5);
        assertThat(r.price()).isEqualByComparingTo("50");
    }

    @Test @DisplayName("ShippingConfigResponse record") void shippingConfigResponse() {
        ShippingConfigResponse r = new ShippingConfigResponse(true, true,
                BigDecimal.valueOf(50), BigDecimal.valueOf(2), "Calle 1", BigDecimal.ZERO, true, BigDecimal.valueOf(500));
        assertThat(r.nationalEnabled()).isTrue();
        assertThat(r.freeShippingMinAmount()).isEqualByComparingTo("500");
    }

    @Test @DisplayName("ShippingConfigAdminResponse record") void shippingConfigAdminResponse() {
        ShippingConfigAdminResponse r = new ShippingConfigAdminResponse(
                true, true, BigDecimal.valueOf(50), BigDecimal.valueOf(2),
                "Calle 1", true, BigDecimal.ZERO,
                true, "Calle", "06600", "CDMX", "CDMX", "MX",
                "Juan", "j@b.com", "5551234567",
                1.0, 20, 15, 10,
                true, BigDecimal.valueOf(500));
        assertThat(r.hasSkydropxCredentials()).isTrue();
        assertThat(r.skydropxSenderName()).isEqualTo("Juan");
    }

    @Test @DisplayName("ShippingMethodResponse record") void shippingMethodResponse() {
        ShippingMethodResponse r = new ShippingMethodResponse(1L, "Express", "Envío rápido",
                BigDecimal.valueOf(150), 2, true, 1);
        assertThat(r.name()).isEqualTo("Express");
        assertThat(r.active()).isTrue();
    }

    @Test @DisplayName("SkydropxRateDto record") void skydropxRateDto() {
        SkydropxRateDto r = new SkydropxRateDto("id1", "FedEx", "Express", 250.0, 2);
        assertThat(r.carrier()).isEqualTo("FedEx");
        assertThat(r.price()).isEqualTo(250.0);
    }

    @Test @DisplayName("SkydropxQuotationResponse record") void skydropxQuotation() {
        SkydropxRateDto rate = new SkydropxRateDto("id1", "FedEx", "Express", 250.0, 2);
        SkydropxQuotationResponse r = new SkydropxQuotationResponse("QUO-01", List.of(rate));
        assertThat(r.quotationId()).isEqualTo("QUO-01");
        assertThat(r.rates()).hasSize(1);
    }

    @Test @DisplayName("SkydropxShipmentResponse record") void skydropxShipment() {
        SkydropxShipmentResponse r = new SkydropxShipmentResponse("SHP-01", "TRK-01", "FedEx", "http://label", "CREATED");
        assertThat(r.trackingNumber()).isEqualTo("TRK-01");
        assertThat(r.status()).isEqualTo("CREATED");
    }

    @Test @DisplayName("ShippingRatesResponse record") void shippingRatesResponse() {
        ShippingRatesResponse r = new ShippingRatesResponse(true, 20.0, BigDecimal.valueOf(100), "QUO-01", List.of());
        assertThat(r.skydropxAvailable()).isTrue();
        assertThat(r.distanceKm()).isEqualTo(20.0);
    }

    @Test @DisplayName("PickupLocationResponse record") void pickupLocationResponse() {
        PickupLocationResponse r = new PickupLocationResponse(1L, "Sucursal Centro", "Av. Juárez 1", "CDMX", "CDMX", true);
        assertThat(r.name()).isEqualTo("Sucursal Centro");
        assertThat(r.active()).isTrue();
    }

    @Test @DisplayName("PickupTimeSlotResponse record") void pickupTimeSlotResponse() {
        PickupTimeSlotResponse r = new PickupTimeSlotResponse(1L, "10:00-12:00", true);
        assertThat(r.label()).isEqualTo("10:00-12:00");
    }

    @Test @DisplayName("PickupExceptionResponse record") void pickupExceptionResponse() {
        LocalDateTime now = LocalDateTime.now();
        PickupExceptionResponse r = new PickupExceptionResponse(1L, LocalDate.of(2026, 5, 1), "Festivo", now);
        assertThat(r.reason()).isEqualTo("Festivo");
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test @DisplayName("PickupAvailabilityResponse record") void pickupAvailabilityResponse() {
        PickupAvailabilityResponse r = new PickupAvailabilityResponse(
                1L, AvailabilityType.RECURRING, DayOfWeek.MONDAY, null,
                LocalTime.of(10, 0), LocalTime.of(14, 0), 20, true, List.of());
        assertThat(r.type()).isEqualTo(AvailabilityType.RECURRING);
        assertThat(r.maxCapacity()).isEqualTo(20);
    }

    @Test @DisplayName("PromoBannerResponse record") void promoBannerResponse() {
        LocalDateTime now = LocalDateTime.now();
        PromoBannerResponse r = new PromoBannerResponse(1L, "img.jpg", "http://link", true, now);
        assertThat(r.imageUrl()).isEqualTo("img.jpg");
        assertThat(r.active()).isTrue();
    }

    @Test @DisplayName("PromotionResponse record y nested") void promotionResponse() {
        PromotionResponse.PromotionProductSummary p =
                new PromotionResponse.PromotionProductSummary(1L, "Camiseta", BigDecimal.valueOf(100));
        PromotionResponse r = new PromotionResponse(1L, "Verano", BigDecimal.valueOf(20),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), true, List.of(p));
        assertThat(r.name()).isEqualTo("Verano");
        assertThat(r.products()).hasSize(1);
        assertThat(p.name()).isEqualTo("Camiseta");
    }

    @Test @DisplayName("StoreInfoResponse record") void storeInfoResponse() {
        StoreInfoResponse r = new StoreInfoResponse("Mi Tienda", "sobre nosotros", "misión",
                "visión", "555", "a@b.com", "privacidad", "logo.jpg",
                "default", "sans", List.of(), "ig_url", "fb_url", "+521234567890");
        assertThat(r.name()).isEqualTo("Mi Tienda");
        assertThat(r.themeKey()).isEqualTo("default");
    }

    @Test @DisplayName("TicketResponse record") void ticketResponse() {
        LocalDateTime now = LocalDateTime.now();
        TicketResponse r = new TicketResponse(1L, 2L, "ORD-001", 3L, "Ana",
                "No llegó", "Mi pedido no ha llegado", TicketStatus.OPEN,
                null, now, now);
        assertThat(r.subject()).isEqualTo("No llegó");
        assertThat(r.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test @DisplayName("UpcomingScheduleResponse record y nested") void upcomingScheduleResponse() {
        OrderResponse order = OrderResponse.builder().id(1L).build();
        UpcomingScheduleResponse.PickupGroupResponse group =
                new UpcomingScheduleResponse.PickupGroupResponse("Sucursal", List.of(order));
        UpcomingScheduleResponse r = new UpcomingScheduleResponse(List.of(order), List.of(group));
        assertThat(r.shipments()).hasSize(1);
        assertThat(r.pickups().get(0).locationName()).isEqualTo("Sucursal");
    }
}
