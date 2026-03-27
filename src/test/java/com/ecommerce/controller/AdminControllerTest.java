package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.CouponRequest;
import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.request.PromoBannerRequest;
import com.ecommerce.dto.request.PromotionRequest;
import com.ecommerce.dto.request.ShippingConfigRequest;
import com.ecommerce.dto.request.TicketUpdateRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.CouponResponse;
import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PickupLocationResponse;
import com.ecommerce.dto.response.PromoBannerResponse;
import com.ecommerce.dto.response.PromotionResponse;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.ShippingConfigAdminResponse;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.TicketStatus;
import com.ecommerce.entity.User;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.dto.response.InventoryItem;
import com.ecommerce.dto.response.ProductSalesItem;
import com.ecommerce.dto.response.SalesDataPoint;
import com.ecommerce.dto.response.SalesReportResponse;
import com.ecommerce.dto.response.UpcomingScheduleResponse;
import com.ecommerce.service.AnalyticsService;
import com.ecommerce.service.CouponService;
import com.ecommerce.service.EmailService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.PickupLocationService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.PromoBannerService;
import com.ecommerce.service.PromotionService;
import com.ecommerce.service.ReportService;
import com.ecommerce.service.ReviewService;
import com.ecommerce.service.ShippingConfigService;
import com.ecommerce.service.SkydropxService;
import com.ecommerce.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = AdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── All service / repository dependencies must be mocked ─────────────────
    @MockBean AnalyticsService analyticsService;
    @MockBean ReportService reportService;
    @MockBean EmailService emailService;
    @MockBean OrderService orderService;
    @MockBean ProductService productService;
    @MockBean PromotionService promotionService;
    @MockBean PromoBannerService promoBannerService;
    @MockBean CouponService couponService;
    @MockBean ReviewService reviewService;
    @MockBean TicketService ticketService;
    @MockBean ShippingConfigService shippingConfigService;
    @MockBean PickupLocationService pickupLocationService;
    @MockBean SkydropxService skydropxService;
    @MockBean OrderRepository orderRepository;
    @MockBean UserRepository userRepository;

    // ── Stub helpers ─────────────────────────────────────────────────────────

    private DashboardStatsResponse stubDashboard() {
        return DashboardStatsResponse.builder()
                .totalOrders(10L)
                .totalProducts(5L)
                .totalUsers(3L)
                .totalRevenue(BigDecimal.valueOf(999.99))
                .recentOrders(Collections.emptyList())
                .ordersByStatus(Map.of("PENDING", 2L, "DELIVERED", 8L))
                .build();
    }

    private OrderResponse stubOrder(Long id) {
        return OrderResponse.builder()
                .id(id)
                .orderNumber("ORD-00" + id)
                .status("PENDING")
                .totalAmount(BigDecimal.valueOf(59.99))
                .build();
    }

    private UserResponse stubUser(Long id) {
        return new UserResponse(id, "Test", "User", "test" + id + "@example.com", null, "CUSTOMER");
    }

    private User stubUserEntity(Long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail("test" + id + "@example.com");
        u.setRole(Role.CUSTOMER);
        return u;
    }

    private PromotionResponse stubPromotion(Long id) {
        return new PromotionResponse(id, "Summer Sale", BigDecimal.valueOf(20),
                LocalDate.now(), LocalDate.now().plusDays(30), true, Collections.emptyList());
    }

    private CouponResponse stubCoupon(Long id) {
        return new CouponResponse(id, "SAVE20", BigDecimal.valueOf(20),
                LocalDate.now().plusDays(30), true, 0, 100, LocalDateTime.now());
    }

    private PromoBannerResponse stubBanner(Long id) {
        return new PromoBannerResponse(id, "https://img.example.com/banner.jpg", "/shop", true, LocalDateTime.now());
    }

    private ReviewResponse stubReview(Long id) {
        return ReviewResponse.builder()
                .id(id)
                .productId(1L)
                .userId(1L)
                .userName("Test User")
                .rating(5)
                .title("Great product")
                .comment("Love it!")
                .approved(false)
                .build();
    }

    private TicketResponse stubTicket(Long id) {
        return new TicketResponse(id, 1L, "ORD-001", 1L, "Test User",
                "Help needed", "Description", TicketStatus.OPEN, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private PickupLocationResponse stubPickupLocation(Long id) {
        return new PickupLocationResponse(id, "Sucursal Centro", "Calle 5 de Mayo 10",
                "Ciudad de México", "CDMX", true);
    }


    private ShippingConfigAdminResponse stubShippingConfig() {
        return new ShippingConfigAdminResponse(
                true, true,
                BigDecimal.valueOf(50), BigDecimal.valueOf(5),
                "Calle Falsa 123", false,
                BigDecimal.valueOf(0),
                false, null, null, null, null, "MX",
                null, null, null,
                1.0, 30, 20, 15,
                false, null);
    }

    // ─── GET /api/admin/dashboard ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/dashboard")
    class GetDashboard {

        @Test
        @DisplayName("returns 200 with dashboard stats")
        void getDashboard_200() throws Exception {
            when(analyticsService.getDashboardStats()).thenReturn(stubDashboard());

            mockMvc.perform(get("/api/admin/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalOrders").value(10))
                    .andExpect(jsonPath("$.data.totalRevenue").value(999.99));
        }
    }

    // ─── GET /api/admin/orders ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders")
    class GetAllOrders {

        @Test
        @DisplayName("returns 200 with paginated orders")
        void getAllOrders_200() throws Exception {
            List<OrderResponse> orders = List.of(stubOrder(1L));
            Page<OrderResponse> page = new PageImpl<>(orders, PageRequest.of(0, 20), orders.size());
            when(orderService.getAllOrders(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-001"));
        }
    }

    // ─── GET /api/admin/orders/{id} ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("returns 200 with order when found")
        void getOrderById_200() throws Exception {
            when(orderService.getOrderById(1L)).thenReturn(stubOrder(1L));

            mockMvc.perform(get("/api/admin/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("propagates 404 when order not found")
        void getOrderById_404() throws Exception {
            when(orderService.getOrderById(99L))
                    .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

            mockMvc.perform(get("/api/admin/orders/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── PUT /api/admin/orders/{id}/status ───────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/orders/{id}/status")
    class UpdateOrderStatus {

        @Test
        @DisplayName("returns 200 with updated order on valid request")
        void updateOrderStatus_200() throws Exception {
            OrderResponse updated = stubOrder(1L);
            updated.setStatus("CONFIRMED");
            when(orderService.updateOrderStatus(anyLong(), any())).thenReturn(updated);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);

            mockMvc.perform(put("/api/admin/orders/1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("returns 400 when status is null")
        void updateOrderStatus_400() throws Exception {
            mockMvc.perform(put("/api/admin/orders/1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\": null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── GET /api/admin/users ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetAllUsers {

        @Test
        @DisplayName("returns 200 with list of users")
        void getAllUsers_200() throws Exception {
            when(userRepository.findAll()).thenReturn(List.of(stubUserEntity(1L), stubUserEntity(2L)));

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }

        @Test
        @DisplayName("returns 200 with empty list when no users")
        void getAllUsers_200_empty() throws Exception {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── GET /api/admin/users/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users/{id}")
    class GetUserById {

        @Test
        @DisplayName("returns 200 with user when found")
        void getUserById_200() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(stubUserEntity(1L)));

            mockMvc.perform(get("/api/admin/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value("test1@example.com"));
        }

        @Test
        @DisplayName("returns 404 when user not found")
        void getUserById_404() throws Exception {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/admin/users/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── GET /api/admin/promotions ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/promotions")
    class GetAllPromotions {

        @Test
        @DisplayName("returns 200 with list of promotions")
        void getAllPromotions_200() throws Exception {
            when(promotionService.getAllPromotions()).thenReturn(List.of(stubPromotion(1L)));

            mockMvc.perform(get("/api/admin/promotions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Summer Sale"))
                    .andExpect(jsonPath("$.data[0].active").value(true));
        }
    }

    // ─── POST /api/admin/promotions ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/promotions")
    class CreatePromotion {

        @Test
        @DisplayName("returns 200 with created promotion on valid request")
        void createPromotion_200() throws Exception {
            when(promotionService.createPromotion(any())).thenReturn(stubPromotion(1L));

            PromotionRequest req = new PromotionRequest(
                    "Summer Sale", BigDecimal.valueOf(20),
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of(1L, 2L));

            mockMvc.perform(post("/api/admin/promotions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Summer Sale"));
        }

        @Test
        @DisplayName("returns 400 when name is blank")
        void createPromotion_400_blankName() throws Exception {
            mockMvc.perform(post("/api/admin/promotions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"discountPercent\":10,\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\",\"productIds\":[1]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── PUT /api/admin/promotions/{id} ──────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/promotions/{id}")
    class UpdatePromotion {

        @Test
        @DisplayName("returns 200 with updated promotion")
        void updatePromotion_200() throws Exception {
            when(promotionService.updatePromotion(anyLong(), any())).thenReturn(stubPromotion(1L));

            PromotionRequest req = new PromotionRequest(
                    "Summer Sale", BigDecimal.valueOf(20),
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of(1L));

            mockMvc.perform(put("/api/admin/promotions/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    // ─── DELETE /api/admin/promotions/{id} ───────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/admin/promotions/{id}")
    class DeletePromotion {

        @Test
        @DisplayName("returns 200 on successful delete")
        void deletePromotion_200() throws Exception {
            doNothing().when(promotionService).deletePromotion(1L);

            mockMvc.perform(delete("/api/admin/promotions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(promotionService).deletePromotion(1L);
        }

        @Test
        @DisplayName("propagates 404 when promotion not found")
        void deletePromotion_404() throws Exception {
            doThrow(new ResourceNotFoundException("Promotion", "id", 99L))
                    .when(promotionService).deletePromotion(99L);

            mockMvc.perform(delete("/api/admin/promotions/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── PATCH /api/admin/promotions/{id}/toggle ─────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/promotions/{id}/toggle")
    class TogglePromotion {

        @Test
        @DisplayName("returns 200 with toggled promotion")
        void togglePromotion_200() throws Exception {
            PromotionResponse toggled = new PromotionResponse(1L, "Summer Sale",
                    BigDecimal.valueOf(20), LocalDate.now(), LocalDate.now().plusDays(30), false, Collections.emptyList());
            when(promotionService.togglePromotion(1L)).thenReturn(toggled);

            mockMvc.perform(patch("/api/admin/promotions/1/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }
    }

    // ─── GET /api/admin/banners ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/banners")
    class GetAllBanners {

        @Test
        @DisplayName("returns 200 with list of banners")
        void getAllBanners_200() throws Exception {
            when(promoBannerService.getAllBanners()).thenReturn(List.of(stubBanner(1L)));

            mockMvc.perform(get("/api/admin/banners"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].imageUrl").value("https://img.example.com/banner.jpg"));
        }
    }

    // ─── POST /api/admin/banners ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/banners")
    class CreateBanner {

        @Test
        @DisplayName("returns 200 with created banner on valid request")
        void createBanner_200() throws Exception {
            when(promoBannerService.createBanner(any())).thenReturn(stubBanner(1L));

            PromoBannerRequest req = new PromoBannerRequest("https://img.example.com/banner.jpg", "/shop");

            mockMvc.perform(post("/api/admin/banners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("returns 400 when imageUrl is blank")
        void createBanner_400_blankImageUrl() throws Exception {
            mockMvc.perform(post("/api/admin/banners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"imageUrl\":\"\",\"linkUrl\":\"/shop\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── DELETE /api/admin/banners/{id} ──────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/admin/banners/{id}")
    class DeleteBanner {

        @Test
        @DisplayName("returns 200 on successful delete")
        void deleteBanner_200() throws Exception {
            doNothing().when(promoBannerService).deleteBanner(1L);

            mockMvc.perform(delete("/api/admin/banners/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(promoBannerService).deleteBanner(1L);
        }
    }

    // ─── GET /api/admin/coupons ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/coupons")
    class GetAllCoupons {

        @Test
        @DisplayName("returns 200 with list of coupons")
        void getAllCoupons_200() throws Exception {
            when(couponService.getAllCoupons()).thenReturn(List.of(stubCoupon(1L)));

            mockMvc.perform(get("/api/admin/coupons"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("SAVE20"))
                    .andExpect(jsonPath("$.data[0].active").value(true));
        }
    }

    // ─── POST /api/admin/coupons ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/coupons")
    class CreateCoupon {

        @Test
        @DisplayName("returns 200 with created coupon on valid request")
        void createCoupon_200() throws Exception {
            when(couponService.createCoupon(any())).thenReturn(stubCoupon(1L));

            CouponRequest req = new CouponRequest("SAVE20", BigDecimal.valueOf(20),
                    LocalDate.now().plusDays(30), 100);

            mockMvc.perform(post("/api/admin/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("SAVE20"));
        }

        @Test
        @DisplayName("returns 400 when code is blank")
        void createCoupon_400_blankCode() throws Exception {
            mockMvc.perform(post("/api/admin/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"\",\"discountPercent\":10,\"expiresAt\":\"2027-01-01\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── DELETE /api/admin/coupons/{id} ──────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/admin/coupons/{id}")
    class DeleteCoupon {

        @Test
        @DisplayName("returns 200 on successful delete")
        void deleteCoupon_200() throws Exception {
            doNothing().when(couponService).deleteCoupon(1L);

            mockMvc.perform(delete("/api/admin/coupons/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(couponService).deleteCoupon(1L);
        }
    }

    // ─── PATCH /api/admin/coupons/{id}/toggle ────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/coupons/{id}/toggle")
    class ToggleCoupon {

        @Test
        @DisplayName("returns 200 with toggled coupon")
        void toggleCoupon_200() throws Exception {
            CouponResponse toggled = new CouponResponse(1L, "SAVE20", BigDecimal.valueOf(20),
                    LocalDate.now().plusDays(30), false, 0, 100, LocalDateTime.now());
            when(couponService.toggleCoupon(1L)).thenReturn(toggled);

            mockMvc.perform(patch("/api/admin/coupons/1/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }
    }

    // ─── GET /api/admin/reviews/pending ──────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reviews/pending")
    class GetPendingReviews {

        @Test
        @DisplayName("returns 200 with pending reviews")
        void getPendingReviews_200() throws Exception {
            when(reviewService.getPendingReviews()).thenReturn(List.of(stubReview(1L)));

            mockMvc.perform(get("/api/admin/reviews/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].approved").value(false))
                    .andExpect(jsonPath("$.data[0].title").value("Great product"));
        }
    }

    // ─── PATCH /api/admin/reviews/{id}/approve ───────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/reviews/{id}/approve")
    class ApproveReview {

        @Test
        @DisplayName("returns 200 with approved review")
        void approveReview_200() throws Exception {
            ReviewResponse approved = ReviewResponse.builder()
                    .id(1L).rating(5).approved(true).title("Great product").build();
            when(reviewService.approveReview(1L)).thenReturn(approved);

            mockMvc.perform(patch("/api/admin/reviews/1/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.approved").value(true));
        }
    }

    // ─── GET /api/admin/tickets ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/tickets")
    class GetAllTickets {

        @Test
        @DisplayName("returns 200 with list of tickets")
        void getAllTickets_200() throws Exception {
            when(ticketService.getAllTickets()).thenReturn(List.of(stubTicket(1L)));

            mockMvc.perform(get("/api/admin/tickets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].status").value("OPEN"));
        }
    }

    // ─── PUT /api/admin/tickets/{id} ──────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/tickets/{id}")
    class UpdateTicket {

        @Test
        @DisplayName("returns 200 with updated ticket")
        void updateTicket_200() throws Exception {
            TicketResponse resolved = new TicketResponse(1L, 1L, "ORD-001", 1L, "Test User",
                    "Help needed", "Description", TicketStatus.RESOLVED, "Issue resolved",
                    LocalDateTime.now(), LocalDateTime.now());
            when(ticketService.updateTicket(anyLong(), any())).thenReturn(resolved);

            TicketUpdateRequest req = new TicketUpdateRequest(TicketStatus.RESOLVED, "Issue resolved");

            mockMvc.perform(put("/api/admin/tickets/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.adminNotes").value("Issue resolved"));
        }

        @Test
        @DisplayName("returns 400 when status is null")
        void updateTicket_400() throws Exception {
            mockMvc.perform(put("/api/admin/tickets/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── GET /api/admin/shipping/config ──────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/shipping/config")
    class GetShippingConfig {

        @Test
        @DisplayName("returns 200 with admin shipping config")
        void getShippingConfig_200() throws Exception {
            when(shippingConfigService.getAdminConfig()).thenReturn(stubShippingConfig());

            mockMvc.perform(get("/api/admin/shipping/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nationalEnabled").value(true))
                    .andExpect(jsonPath("$.data.pickupEnabled").value(true));
        }
    }

    // ─── PUT /api/admin/shipping/config ──────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/shipping/config")
    class UpdateShippingConfig {

        @Test
        @DisplayName("returns 200 with updated shipping config")
        void updateShippingConfig_200() throws Exception {
            when(shippingConfigService.updateConfig(any())).thenReturn(stubShippingConfig());

            ShippingConfigRequest req = new ShippingConfigRequest(
                    true, true, BigDecimal.valueOf(50), BigDecimal.valueOf(5),
                    "Calle Falsa 123", null, BigDecimal.ZERO,
                    null, null, null, null, null, null, null,
                    null, 1.0, 30, 20, 15,
                    null, null);

            mockMvc.perform(put("/api/admin/shipping/config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ─── GET /api/admin/pickup-locations ─────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/pickup-locations")
    class GetAllPickupLocations {

        @Test
        @DisplayName("returns 200 with list of pickup locations")
        void getAllPickupLocations_200() throws Exception {
            when(pickupLocationService.getAllLocations()).thenReturn(List.of(stubPickupLocation(1L)));

            mockMvc.perform(get("/api/admin/pickup-locations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Sucursal Centro"))
                    .andExpect(jsonPath("$.data[0].active").value(true));
        }
    }

    // ─── POST /api/admin/pickup-locations ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/pickup-locations")
    class CreatePickupLocation {

        @Test
        @DisplayName("returns 200 with created pickup location")
        void createPickupLocation_200() throws Exception {
            when(pickupLocationService.create(any())).thenReturn(stubPickupLocation(1L));

            PickupLocationRequest req = new PickupLocationRequest(
                    "Sucursal Centro", "Calle 5 de Mayo 10", "Ciudad de México", "CDMX");

            mockMvc.perform(post("/api/admin/pickup-locations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Sucursal Centro"));
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void createPickupLocation_400() throws Exception {
            mockMvc.perform(post("/api/admin/pickup-locations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── DELETE /api/admin/pickup-locations/{id} ─────────────────────────────

    @Nested
    @DisplayName("DELETE /api/admin/pickup-locations/{id}")
    class DeletePickupLocation {

        @Test
        @DisplayName("returns 200 on successful delete")
        void deletePickupLocation_200() throws Exception {
            doNothing().when(pickupLocationService).delete(1L);

            mockMvc.perform(delete("/api/admin/pickup-locations/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(pickupLocationService).delete(1L);
        }
    }

    // ─── POST /api/admin/pickup-locations/{id}/time-slots ────────────────────

    // ─── GET /api/admin/reports/sales ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reports/sales")
    class GetSalesReport {

        @Test
        @DisplayName("returns 200 with sales report for default period (month)")
        void getSalesReport_200_defaultPeriod() throws Exception {
            SalesReportResponse report = new SalesReportResponse(
                    "Este mes",
                    List.of(new SalesDataPoint("2025-03-01", 3, java.math.BigDecimal.valueOf(1500))),
                    java.math.BigDecimal.valueOf(1500), 3L);
            when(reportService.getSalesReport("month")).thenReturn(report);

            mockMvc.perform(get("/api/admin/reports/sales").param("period", "month"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.period").value("Este mes"))
                    .andExpect(jsonPath("$.data.totalOrders").value(3))
                    .andExpect(jsonPath("$.data.data").isArray());
        }

        @Test
        @DisplayName("returns 200 with weekly report")
        void getSalesReport_200_week() throws Exception {
            SalesReportResponse report = new SalesReportResponse(
                    "Última semana", List.of(),
                    java.math.BigDecimal.ZERO, 0L);
            when(reportService.getSalesReport("week")).thenReturn(report);

            mockMvc.perform(get("/api/admin/reports/sales").param("period", "week"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("Última semana"));
        }
    }

    // ─── GET /api/admin/reports/products/top-selling ──────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reports/products/top-selling")
    class GetTopSellingProducts {

        @Test
        @DisplayName("returns 200 with list of top-selling products")
        void getTopSelling_200() throws Exception {
            List<ProductSalesItem> items = List.of(
                    new ProductSalesItem(1L, "Camiseta", 100L, java.math.BigDecimal.valueOf(2999), 20));
            when(reportService.getTopSellingProducts(20)).thenReturn(items);

            mockMvc.perform(get("/api/admin/reports/products/top-selling"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].productName").value("Camiseta"))
                    .andExpect(jsonPath("$.data[0].unitsSold").value(100));
        }

        @Test
        @DisplayName("returns 200 with empty list when no sales data")
        void getTopSelling_200_empty() throws Exception {
            when(reportService.getTopSellingProducts(20)).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/reports/products/top-selling"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── GET /api/admin/reports/products/least-selling ───────────────────────

    @Nested
    @DisplayName("GET /api/admin/reports/products/least-selling")
    class GetLeastSellingProducts {

        @Test
        @DisplayName("returns 200 with list of least-selling products")
        void getLeastSelling_200() throws Exception {
            List<ProductSalesItem> items = List.of(
                    new ProductSalesItem(2L, "Pantalón", 0L, java.math.BigDecimal.ZERO, 5));
            when(reportService.getLeastSellingProducts(20)).thenReturn(items);

            mockMvc.perform(get("/api/admin/reports/products/least-selling"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].unitsSold").value(0));
        }
    }

    // ─── GET /api/admin/reports/inventory ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reports/inventory")
    class GetInventoryReport {

        @Test
        @DisplayName("returns 200 with full inventory list")
        void getInventory_200() throws Exception {
            List<InventoryItem> items = List.of(
                    new InventoryItem(1L, "Camiseta", "SKU-1", 50,
                            java.math.BigDecimal.valueOf(29.99), true));
            when(reportService.getInventoryReport()).thenReturn(items);

            mockMvc.perform(get("/api/admin/reports/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Camiseta"))
                    .andExpect(jsonPath("$.data[0].stock").value(50));
        }

        @Test
        @DisplayName("returns 200 with empty list when no products")
        void getInventory_200_empty() throws Exception {
            when(reportService.getInventoryReport()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/reports/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── GET /api/admin/reports/out-of-stock ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reports/out-of-stock")
    class GetOutOfStockProducts {

        @Test
        @DisplayName("returns 200 with zero-stock products only")
        void getOutOfStock_200() throws Exception {
            List<InventoryItem> items = List.of(
                    new InventoryItem(3L, "Agotado", "SKU-3", 0,
                            java.math.BigDecimal.TEN, true));
            when(reportService.getOutOfStockProducts()).thenReturn(items);

            mockMvc.perform(get("/api/admin/reports/out-of-stock"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].stock").value(0));
        }

        @Test
        @DisplayName("returns 200 with empty list when all products have stock")
        void getOutOfStock_200_empty() throws Exception {
            when(reportService.getOutOfStockProducts()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/reports/out-of-stock"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── GET /api/admin/orders/upcoming-schedule ──────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders/upcoming-schedule")
    class GetUpcomingSchedule {

        @Test
        @DisplayName("returns 200 with shipments and pickup groups")
        void getUpcomingSchedule_200() throws Exception {
            OrderResponse shipment = stubOrder(1L);
            UpcomingScheduleResponse schedule = new UpcomingScheduleResponse(
                    List.of(shipment),
                    List.of(new UpcomingScheduleResponse.PickupGroupResponse(
                            "Sucursal Norte", List.of(stubOrder(2L)))));
            when(orderService.getUpcomingSchedule()).thenReturn(schedule);

            mockMvc.perform(get("/api/admin/orders/upcoming-schedule"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.shipments").isArray())
                    .andExpect(jsonPath("$.data.shipments[0].orderNumber").value("ORD-001"))
                    .andExpect(jsonPath("$.data.pickups").isArray())
                    .andExpect(jsonPath("$.data.pickups[0].locationName").value("Sucursal Norte"))
                    .andExpect(jsonPath("$.data.pickups[0].orders").isArray());
        }

        @Test
        @DisplayName("returns 200 with empty schedule when no active orders")
        void getUpcomingSchedule_200_empty() throws Exception {
            UpcomingScheduleResponse empty = new UpcomingScheduleResponse(List.of(), List.of());
            when(orderService.getUpcomingSchedule()).thenReturn(empty);

            mockMvc.perform(get("/api/admin/orders/upcoming-schedule"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shipments").isEmpty())
                    .andExpect(jsonPath("$.data.pickups").isEmpty());
        }
    }

    // ─── POST /api/admin/pickup-locations/{id}/time-slots ────────────────────

}
