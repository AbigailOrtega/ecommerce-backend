package com.ecommerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("OrderController — Integration")
class OrderControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String customerToken;
    private String adminToken;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        customerToken = obtainToken("test@test.com",  "Test123!");
        adminToken    = obtainToken("admin@test.com", "Admin123!");
    }

    private String obtainToken(String email, String password) {
        Map<String, String> credentials = Map.of("email", email, "password", password);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(credentials, jsonHeaders()),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("accessToken").asText();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("GET /api/orders — returns 200 with seeded orders for customer")
    void getUserOrders_200() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
        // The e2e seeder creates ORD-E2E-NATL and ORD-E2E-PKUP for the customer
        assertThat(response.getBody().path("data").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("GET /api/orders — returns 401 without token")
    void getUserOrders_401() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/orders/{orderNumber} ─────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("GET /api/orders/{orderNumber} — returns 200 for seeded NATIONAL order")
    void getOrderByNumber_200_national() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders/ORD-E2E-NATL",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("orderNumber").asText()).isEqualTo("ORD-E2E-NATL");
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
        assertThat(data.path("shippingType").asText()).isEqualTo("NATIONAL");
        assertThat(data.path("items").isArray()).isTrue();
        assertThat(data.path("items").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("GET /api/orders/{orderNumber} — returns 200 for seeded PICKUP order")
    void getOrderByNumber_200_pickup() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders/ORD-E2E-PKUP",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("orderNumber").asText()).isEqualTo("ORD-E2E-PKUP");
        assertThat(data.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(data.path("shippingType").asText()).isEqualTo("PICKUP");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("GET /api/orders/{orderNumber} — returns 404 for unknown order number")
    void getOrderByNumber_404() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders/ORD-DOES-NOT-EXIST",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("GET /api/orders/{orderNumber} — returns 400 when order belongs to a different user")
    void getOrderByNumber_400_wrongUser() {
        // Admin account does not own the seeded customer orders
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders/ORD-E2E-NATL",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        // The service throws BadRequestException → 400
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("GET /api/orders/{orderNumber} — returns 401 without token")
    void getOrderByNumber_401() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/orders/ORD-E2E-NATL",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/orders — happy path ─────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("POST /api/orders — returns 201 when cart has items and request is valid")
    void createOrder_201() {
        // Step 1: add a product to the customer's cart
        Long productId = resolveProductId("test-jeans");
        Map<String, Object> cartBody = Map.of("productId", productId, "quantity", 1);
        ResponseEntity<JsonNode> addToCartResponse = restTemplate.postForEntity(
                baseUrl() + "/api/cart",
                new HttpEntity<>(cartBody, authHeaders(customerToken)),
                JsonNode.class
        );
        assertThat(addToCartResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 2: place the order
        Map<String, Object> orderBody = Map.of(
                "paymentMethod", "COD",
                "shippingType", "NATIONAL",
                "shippingAddress", "Calle Falsa 123",
                "shippingCity", "Ciudad de México",
                "shippingState", "CDMX",
                "shippingZipCode", "06600",
                "shippingCountry", "MX"
        );
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders",
                new HttpEntity<>(orderBody, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("message").asText()).isEqualTo("Order placed successfully");

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("orderNumber").asText()).isNotBlank();
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
        assertThat(data.path("paymentMethod").asText()).isEqualTo("COD");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("POST /api/orders — returns 400 when cart is empty")
    void createOrder_400_emptyCart() {
        // Clear the cart first to guarantee it is empty
        restTemplate.exchange(baseUrl() + "/api/cart",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class);

        Map<String, Object> orderBody = Map.of(
                "paymentMethod", "COD",
                "shippingType", "NATIONAL",
                "shippingAddress", "Calle Falsa 123",
                "shippingCity", "CDMX",
                "shippingCountry", "MX"
        );
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders",
                new HttpEntity<>(orderBody, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("POST /api/orders — returns 400 when paymentMethod is missing")
    void createOrder_400_missingPaymentMethod() {
        Map<String, Object> orderBody = Map.of(
                "shippingType", "NATIONAL",
                "shippingAddress", "Calle Falsa 123",
                "shippingCity", "CDMX",
                "shippingCountry", "MX"
        );
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders",
                new HttpEntity<>(orderBody, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("POST /api/orders — returns 401 without token")
    void createOrder_401() {
        Map<String, Object> orderBody = Map.of(
                "paymentMethod", "COD",
                "shippingType", "NATIONAL"
        );
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders",
                new HttpEntity<>(orderBody, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private Long resolveProductId(String slug) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + slug, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("id").asLong();
    }
}
