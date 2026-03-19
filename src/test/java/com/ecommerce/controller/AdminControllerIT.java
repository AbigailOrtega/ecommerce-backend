package com.ecommerce.controller;

import com.ecommerce.dto.request.CouponRequest;
import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.request.PickupTimeSlotRequest;
import com.ecommerce.dto.request.PromoBannerRequest;
import com.ecommerce.dto.request.PromotionRequest;
import com.ecommerce.dto.request.TicketUpdateRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("AdminController — Integration")
class AdminControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String adminToken;
    private String customerToken;

    private String base(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @BeforeEach
    void setUp() {
        // Obtain admin token
        LoginRequest adminLogin = new LoginRequest("admin@test.com", "Admin123!");
        ResponseEntity<ApiResponse> adminResp = restTemplate.postForEntity(
                base("/api/auth/login"),
                adminLogin,
                ApiResponse.class);
        assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> adminData = (Map<?, ?>) adminResp.getBody().getData();
        adminToken = (String) adminData.get("accessToken");

        // Obtain customer token
        LoginRequest customerLogin = new LoginRequest("test@test.com", "Test123!");
        ResponseEntity<ApiResponse> customerResp = restTemplate.postForEntity(
                base("/api/auth/login"),
                customerLogin,
                ApiResponse.class);
        assertThat(customerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> customerData = (Map<?, ?>) customerResp.getBody().getData();
        customerToken = (String) customerData.get("accessToken");
    }

    // ─── Access control: customer must receive 403 on all /api/admin/** ───────

    @Nested
    @DisplayName("Access control — customer gets 403")
    class AccessControl {

        @Test
        @DisplayName("GET /api/admin/dashboard returns 403 for CUSTOMER")
        void dashboard_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/dashboard"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/orders returns 403 for CUSTOMER")
        void orders_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/orders"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/users returns 403 for CUSTOMER")
        void users_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/users"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/promotions returns 403 for CUSTOMER")
        void promotions_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/promotions"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/coupons returns 403 for CUSTOMER")
        void coupons_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/coupons"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/tickets returns 403 for CUSTOMER")
        void tickets_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/tickets"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/shipping/config returns 403 for CUSTOMER")
        void shippingConfig_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/shipping/config"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /api/admin/pickup-locations returns 403 for CUSTOMER")
        void pickupLocations_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/pickup-locations"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("any admin endpoint returns 401 without token")
        void dashboard_401_noToken() {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    base("/api/admin/dashboard"), String.class);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/dashboard")
    class Dashboard {

        @Test
        @DisplayName("returns 200 with stats for ADMIN")
        void getDashboard_200_forAdmin() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/dashboard"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            Map<String, ?> data = (Map<String, ?>) resp.getBody().getData();
            assertThat(data).containsKey("totalOrders");
            assertThat(data).containsKey("totalRevenue");
        }
    }

    // ─── Orders ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders")
    class AdminOrders {

        @Test
        @DisplayName("returns 200 with paginated orders for ADMIN")
        void getAllOrders_200() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/orders"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 404 for non-existent order id")
        void getOrderById_404() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/orders/999999"), HttpMethod.GET, req, String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users")
    class AdminUsers {

        @Test
        @DisplayName("returns 200 with user list for ADMIN")
        void getAllUsers_200() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/users"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            // Seeder guarantees at least 2 users (admin + customer)
            List<?> users = (List<?>) resp.getBody().getData();
            assertThat(users).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("returns 404 for non-existent user id")
        void getUserById_404() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/users/999999"), HttpMethod.GET, req, String.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── Promotions ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Promotions CRUD")
    class Promotions {

        @Test
        @DisplayName("POST creates promotion, DELETE removes it")
        void createAndDeletePromotion() {
            // GET first — must return a list
            HttpEntity<Void> getReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> listResp = restTemplate.exchange(
                    base("/api/admin/promotions"), HttpMethod.GET, getReq, ApiResponse.class);
            assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // POST — create
            PromotionRequest body = new PromotionRequest(
                    "Test Promo", BigDecimal.valueOf(15),
                    LocalDate.now(), LocalDate.now().plusDays(10), List.of());

            HttpEntity<PromotionRequest> createReq = new HttpEntity<>(body, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    base("/api/admin/promotions"), HttpMethod.POST, createReq, ApiResponse.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(createResp.getBody().isSuccess()).isTrue();
            Map<?, ?> created = (Map<?, ?>) createResp.getBody().getData();
            Long id = Long.valueOf(created.get("id").toString());

            // DELETE
            HttpEntity<Void> delReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> delResp = restTemplate.exchange(
                    base("/api/admin/promotions/" + id), HttpMethod.DELETE, delReq, ApiResponse.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── Coupons ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Coupons CRUD")
    class Coupons {

        @Test
        @DisplayName("POST creates coupon, GET lists it, DELETE removes it")
        void createListAndDeleteCoupon() {
            String uniqueCode = "ITCODE" + System.currentTimeMillis();
            CouponRequest body = new CouponRequest(
                    uniqueCode, BigDecimal.valueOf(10),
                    LocalDate.now().plusDays(60), null);

            HttpEntity<CouponRequest> createReq = new HttpEntity<>(body, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    base("/api/admin/coupons"), HttpMethod.POST, createReq, ApiResponse.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> created = (Map<?, ?>) createResp.getBody().getData();
            assertThat(created.get("code")).isEqualTo(uniqueCode);
            Long id = Long.valueOf(created.get("id").toString());

            // GET list
            HttpEntity<Void> listReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> listResp = restTemplate.exchange(
                    base("/api/admin/coupons"), HttpMethod.GET, listReq, ApiResponse.class);
            assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // DELETE
            HttpEntity<Void> delReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> delResp = restTemplate.exchange(
                    base("/api/admin/coupons/" + id), HttpMethod.DELETE, delReq, ApiResponse.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── Pickup Locations ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pickup Locations CRUD")
    class PickupLocations {

        @Test
        @DisplayName("POST creates pickup location, DELETE removes it")
        void createAndDeletePickupLocation() {
            PickupLocationRequest body = new PickupLocationRequest(
                    "Sucursal IT", "Av. Test 999", "Guadalajara", "Jalisco");

            HttpEntity<PickupLocationRequest> createReq = new HttpEntity<>(body, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    base("/api/admin/pickup-locations"), HttpMethod.POST, createReq, ApiResponse.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> created = (Map<?, ?>) createResp.getBody().getData();
            assertThat(created.get("name")).isEqualTo("Sucursal IT");
            Long id = Long.valueOf(created.get("id").toString());

            // DELETE
            HttpEntity<Void> delReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> delResp = restTemplate.exchange(
                    base("/api/admin/pickup-locations/" + id), HttpMethod.DELETE, delReq, ApiResponse.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST time-slot on existing location and DELETE it")
        void addAndDeleteTimeSlot() {
            // Create location first
            PickupLocationRequest locBody = new PickupLocationRequest(
                    "Sucursal Slot", "Calle 1", "CDMX", "CDMX");
            HttpEntity<PickupLocationRequest> createLoc = new HttpEntity<>(locBody, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> locResp = restTemplate.exchange(
                    base("/api/admin/pickup-locations"), HttpMethod.POST, createLoc, ApiResponse.class);
            Long locId = Long.valueOf(((Map<?, ?>) locResp.getBody().getData()).get("id").toString());

            // Add time slot
            PickupTimeSlotRequest slotBody = new PickupTimeSlotRequest("Martes 09:00 – 13:00");
            HttpEntity<PickupTimeSlotRequest> slotReq = new HttpEntity<>(slotBody, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> slotResp = restTemplate.exchange(
                    base("/api/admin/pickup-locations/" + locId + "/time-slots"),
                    HttpMethod.POST, slotReq, ApiResponse.class);
            assertThat(slotResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> slot = (Map<?, ?>) slotResp.getBody().getData();
            assertThat(slot.get("label")).isEqualTo("Martes 09:00 – 13:00");
            Long slotId = Long.valueOf(slot.get("id").toString());

            // DELETE time slot
            HttpEntity<Void> delSlot = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> delResp = restTemplate.exchange(
                    base("/api/admin/pickup-locations/" + locId + "/time-slots/" + slotId),
                    HttpMethod.DELETE, delSlot, ApiResponse.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Cleanup — delete location
            HttpEntity<Void> delLoc = new HttpEntity<>(bearerHeaders(adminToken));
            restTemplate.exchange(
                    base("/api/admin/pickup-locations/" + locId), HttpMethod.DELETE, delLoc, ApiResponse.class);
        }
    }

    // ─── Shipping Config ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/shipping/config")
    class AdminShippingConfig {

        @Test
        @DisplayName("returns 200 with shipping config for ADMIN")
        void getShippingConfig_200() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/shipping/config"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            Map<String, ?> data = (Map<String, ?>) resp.getBody().getData();
            assertThat(data).containsKey("nationalEnabled");
            assertThat(data).containsKey("pickupEnabled");
        }
    }

    // ─── Reviews ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/reviews/pending")
    class AdminReviews {

        @Test
        @DisplayName("returns 200 with pending reviews list for ADMIN")
        void getPendingReviews_200() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/reviews/pending"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("GET /api/admin/reviews/pending returns 403 for CUSTOMER")
        void getPendingReviews_403_forCustomer() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(customerToken));
            ResponseEntity<String> resp = restTemplate.exchange(
                    base("/api/admin/reviews/pending"), HttpMethod.GET, req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ─── Banners ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Banners CRUD")
    class Banners {

        @Test
        @DisplayName("POST creates banner, DELETE removes it")
        void createAndDeleteBanner() {
            PromoBannerRequest body = new PromoBannerRequest("https://img.example.com/test-banner.jpg", "/sale");

            HttpEntity<PromoBannerRequest> createReq = new HttpEntity<>(body, bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> createResp = restTemplate.exchange(
                    base("/api/admin/banners"), HttpMethod.POST, createReq, ApiResponse.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> created = (Map<?, ?>) createResp.getBody().getData();
            Long id = Long.valueOf(created.get("id").toString());

            // DELETE
            HttpEntity<Void> delReq = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> delResp = restTemplate.exchange(
                    base("/api/admin/banners/" + id), HttpMethod.DELETE, delReq, ApiResponse.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── Tickets ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/tickets")
    class AdminTickets {

        @Test
        @DisplayName("returns 200 with ticket list for ADMIN")
        void getAllTickets_200() {
            HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(adminToken));
            ResponseEntity<ApiResponse> resp = restTemplate.exchange(
                    base("/api/admin/tickets"), HttpMethod.GET, req, ApiResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }
}
