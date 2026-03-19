package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("CartController — Integration")
class CartControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;

    private String customerToken;
    private static Long seededItemId;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        customerToken = obtainToken("test@test.com", "Test123!");
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

    private HttpHeaders authHeaders() {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(customerToken);
        return headers;
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/cart — returns 200 with initially empty cart")
    void getCart_200_empty() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/cart — returns 401 without token")
    void getCart_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/cart ────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("POST /api/cart — returns 201 and adds item for existing product")
    void addToCart_201() {
        // Use seeded product id=1 (Test T-Shirt, slug test-t-shirt)
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("productId", productId, "quantity", 2);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/cart",
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("message").asText()).isEqualTo("Item added to cart");
        assertThat(response.getBody().path("data").path("quantity").asInt()).isEqualTo(2);

        // Capture item id for subsequent tests
        seededItemId = response.getBody().path("data").path("id").asLong();
        assertThat(seededItemId).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/cart — returns 400 when productId is missing")
    void addToCart_400_missingProductId() {
        Map<String, Object> body = Map.of("quantity", 1);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/cart",
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/cart — returns 401 without authentication token")
    void addToCart_401_noToken() {
        Map<String, Object> body = Map.of("productId", 1, "quantity", 1);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/cart",
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/cart — returns 404 for non-existent product")
    void addToCart_404_productNotFound() {
        Map<String, Object> body = Map.of("productId", 999999L, "quantity", 1);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/cart",
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    // ── GET /api/cart (after adding items) ───────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("GET /api/cart — returns items after they have been added")
    void getCart_200_withItems() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").isArray()).isTrue();
        assertThat(response.getBody().path("data").size()).isGreaterThanOrEqualTo(1);
    }

    // ── PUT /api/cart/{itemId} ────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("PUT /api/cart/{itemId} — returns 200 and updates quantity")
    void updateCartItem_200() {
        Assumptions.assumeTrue(seededItemId != null, "Requires item created in order 3");

        Map<String, Integer> body = Map.of("quantity", 5);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart/" + seededItemId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("quantity").asInt()).isEqualTo(5);
    }

    @Test
    @Order(9)
    @DisplayName("PUT /api/cart/{itemId} — returns 404 for non-existent item")
    void updateCartItem_404() {
        Map<String, Integer> body = Map.of("quantity", 1);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart/999999",
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(10)
    @DisplayName("PUT /api/cart/{itemId} — returns 401 without token")
    void updateCartItem_401() {
        Map<String, Integer> body = Map.of("quantity", 1);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart/1",
                HttpMethod.PUT,
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── DELETE /api/cart/{itemId} ─────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("DELETE /api/cart/{itemId} — returns 200 and removes item")
    void removeFromCart_200() {
        Assumptions.assumeTrue(seededItemId != null, "Requires item created in order 3");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart/" + seededItemId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("message").asText()).isEqualTo("Item removed from cart");
    }

    @Test
    @Order(12)
    @DisplayName("DELETE /api/cart/{itemId} — returns 404 for non-existent item")
    void removeFromCart_404() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart/999999",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("DELETE /api/cart — returns 200 and clears all items")
    void clearCart_200() {
        // Add an item first so the cart has something to clear
        Long productId = resolveProductId("test-jeans");
        Map<String, Object> addBody = Map.of("productId", productId, "quantity", 1);
        restTemplate.postForEntity(baseUrl() + "/api/cart",
                new HttpEntity<>(addBody, authHeaders()), JsonNode.class);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("message").asText()).isEqualTo("Cart cleared");
    }

    @Test
    @Order(14)
    @DisplayName("DELETE /api/cart — returns 401 without token")
    void clearCart_401() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Resolves the numeric product id for the given slug by fetching from the public products API.
     * The e2e seeder creates test products with predictable slugs.
     */
    private Long resolveProductId(String slug) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + slug, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("id").asLong();
    }
}
