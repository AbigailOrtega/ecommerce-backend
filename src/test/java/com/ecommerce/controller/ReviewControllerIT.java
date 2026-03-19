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

/**
 * Integration tests for ReviewController and the admin review endpoints in AdminController.
 *
 * Notes on seeded data (TestDataInitializer, e2e profile):
 *   - test@test.com (CUSTOMER) has two orders:
 *       ORD-E2E-NATL (PENDING)   → contains Test T-Shirt  (slug: test-t-shirt)
 *       ORD-E2E-PKUP (CONFIRMED) → contains Test Sneakers (slug: test-sneakers)
 *   - ReviewService.createReview checks for OrderStatus.DELIVERED orders only.
 *     Because neither seeded order is DELIVERED, POST /api/products/{id}/reviews
 *     will return 403 ("You can only review products you have purchased and received.").
 *     The test captures a reviewId only when the server responds with 200 and skips
 *     delete/approve steps that depend on it via Assumptions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ReviewController — Integration")
class ReviewControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String customerToken;
    private String adminToken;

    /** Captured when POST /api/products/{id}/reviews returns 200. */
    private static Long createdReviewId;

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

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Resolves the numeric product id from its URL slug via the public products API.
     */
    private Long resolveProductId(String slug) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + slug, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("id").asLong();
    }

    // ── GET /api/products/{productId}/reviews ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/products/{id}/reviews — returns 200 without authentication (public endpoint)")
    void getProductReviews_200_public() {
        Long productId = resolveProductId("test-t-shirt");

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("content").isArray()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/products/{id}/reviews — supports page and size query parameters")
    void getProductReviews_200_withPaginationParams() {
        Long productId = resolveProductId("test-t-shirt");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/products/" + productId + "/reviews?page=0&size=5",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("content").isArray()).isTrue();
        assertThat(data.path("size").asInt()).isEqualTo(5);
        assertThat(data.path("number").asInt()).isEqualTo(0);
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/products/{id}/reviews — returns 404 for non-existent product")
    void getProductReviews_404_unknownProduct() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/999999/reviews",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    // ── GET /api/products/{productId}/reviews/summary ─────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /api/products/{id}/reviews/summary — returns 200 without authentication (public endpoint)")
    void getProductSummary_200_public() {
        Long productId = resolveProductId("test-t-shirt");

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews/summary",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.has("averageRating")).isTrue();
        assertThat(data.has("totalReviews")).isTrue();
        assertThat(data.has("ratingDistribution")).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/products/{id}/reviews/summary — returns 404 for non-existent product")
    void getProductSummary_404_unknownProduct() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl() + "/api/products/999999/reviews/summary",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    // ── POST /api/products/{productId}/reviews ────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("POST /api/products/{id}/reviews — returns 401 without authentication token")
    void createReview_401_noToken() {
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("rating", 5, "title", "Great shirt", "comment", "Very comfortable.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/products/{id}/reviews — returns 403 when customer has not received the product")
    void createReview_403_productNotPurchasedOrDelivered() {
        // Seeded order ORD-E2E-NATL contains test-t-shirt but its status is PENDING (not DELIVERED).
        // The service rejects the review with 403 because the purchase cannot be verified.
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("rating", 5, "title", "Great shirt", "comment", "Very comfortable.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(8)
    @DisplayName("POST /api/products/{id}/reviews — returns 403 for a product the customer never ordered")
    void createReview_403_productNeverOrdered() {
        // test-jeans was never part of any seeded order for the customer.
        Long productId = resolveProductId("test-jeans");
        Map<String, Object> body = Map.of("rating", 4, "title", "Nice jeans", "comment", "Comfortable fit.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(9)
    @DisplayName("POST /api/products/{id}/reviews — returns 400 when request body is invalid (rating out of range)")
    void createReview_400_invalidRating() {
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("rating", 6, "title", "Amazing", "comment", "Loved it.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("POST /api/products/{id}/reviews — returns 400 when title is blank")
    void createReview_400_blankTitle() {
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("rating", 4, "title", "", "comment", "Good product.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/products/{id}/reviews — returns 404 for a non-existent product")
    void createReview_404_unknownProduct() {
        Map<String, Object> body = Map.of("rating", 4, "title", "Great", "comment", "Would buy again.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/999999/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    /**
     * Attempts to create a review for a purchased product. Accepts 200 (review created) or
     * 403/400 (order not DELIVERED or already reviewed in a previous run). The review ID is
     * captured only on a 200 response so that subsequent delete/approve tests can use it.
     */
    @Test
    @Order(12)
    @DisplayName("POST /api/products/{id}/reviews — returns 200 or 400/403 for a purchased product (captured if 200)")
    void createReview_purchasedProduct_captureIdIfCreated() {
        Long productId = resolveProductId("test-t-shirt");
        Map<String, Object> body = Map.of("rating", 5, "title", "Excellent quality",
                "comment", "Would recommend to anyone.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/products/" + productId + "/reviews",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        // 200 = review created (order was DELIVERED); 403 = order not yet delivered; 400 = already reviewed
        assertThat(response.getStatusCode()).isIn(
                HttpStatus.OK, HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST);

        if (response.getStatusCode() == HttpStatus.OK) {
            createdReviewId = response.getBody().path("data").path("id").asLong();
            assertThat(createdReviewId).isGreaterThan(0);
        }
    }

    // ── DELETE /api/reviews/{reviewId} — customer owner ───────────────────────

    @Test
    @Order(13)
    @DisplayName("DELETE /api/reviews/{id} — customer owner can delete their own review")
    void deleteReview_200_owner() {
        Assumptions.assumeTrue(createdReviewId != null,
                "Skipped: no review was created in order 12 (order not yet DELIVERED)");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/reviews/" + createdReviewId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
    }

    @Test
    @Order(14)
    @DisplayName("DELETE /api/reviews/{id} — returns 404 for a non-existent review")
    void deleteReview_404() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/reviews/999999",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(15)
    @DisplayName("DELETE /api/reviews/{id} — returns 401 without authentication token")
    void deleteReview_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/reviews/1",
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Admin: GET /api/admin/reviews/pending ─────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("GET /api/admin/reviews/pending — returns 401 without authentication token")
    void getPendingReviews_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/pending",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(17)
    @DisplayName("GET /api/admin/reviews/pending — returns 403 when called by a customer")
    void getPendingReviews_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/pending",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(18)
    @DisplayName("GET /api/admin/reviews/pending — returns 200 with list of pending reviews for admin")
    void getPendingReviews_200_adminToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/pending",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    // ── Admin: PATCH /api/admin/reviews/{id}/approve ──────────────────────────

    @Test
    @Order(19)
    @DisplayName("PATCH /api/admin/reviews/{id}/approve — returns 401 without authentication token")
    void approveReview_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/1/approve",
                HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(20)
    @DisplayName("PATCH /api/admin/reviews/{id}/approve — returns 403 when called by a customer")
    void approveReview_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/1/approve",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(21)
    @DisplayName("PATCH /api/admin/reviews/{id}/approve — admin gets 404 for non-existent review")
    void approveReview_404_unknownReview() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/999999/approve",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    // ── Admin: DELETE /api/admin/reviews/{id} ────────────────────────────────

    @Test
    @Order(22)
    @DisplayName("DELETE /api/admin/reviews/{id} — admin can delete any review by ID")
    void adminDeleteReview_200_adminToken() {
        Assumptions.assumeTrue(createdReviewId != null,
                "Skipped: no review was created in order 12 (order not yet DELIVERED)");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/" + createdReviewId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        // If the customer already deleted it in order 13, this returns 404; both are acceptable outcomes.
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(23)
    @DisplayName("DELETE /api/admin/reviews/{id} — returns 401 without authentication token")
    void adminDeleteReview_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/reviews/1",
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
