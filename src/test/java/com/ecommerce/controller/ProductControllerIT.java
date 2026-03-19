package com.ecommerce.controller;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("ProductController — Integration")
class ProductControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String adminToken;
    private String customerToken;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void setUp() {
        adminToken    = obtainToken("admin@test.com", "Admin123!");
        customerToken = obtainToken("test@test.com",  "Test123!");
    }

    @SuppressWarnings("unchecked")
    private String obtainToken(String email, String password) {
        Map<String, String> credentials = Map.of("email", email, "password", password);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"),
                credentials,
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ProductRequest validProductRequest(String name) {
        return new ProductRequest(
                name,
                "Integration test description",
                new BigDecimal("39.99"),
                null,
                null,
                5,
                null,
                List.of(),
                List.of(),
                false,
                true,
                List.of()
        );
    }

    // ─── GET /api/products ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products")
    class GetAllProducts {

        @Test
        @DisplayName("200 — public endpoint returns paginated product list")
        void getAll_200_public() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("success")).isEqualTo(true);
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("content")).isNotNull();
        }
    }

    // ─── GET /api/products/slug/{slug} ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/slug/{slug}")
    class GetProductBySlug {

        @Test
        @DisplayName("200 — returns seeded product by slug without auth")
        void getBySlug_200() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/slug/test-t-shirt"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("success")).isEqualTo(true);
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("slug")).isEqualTo("test-t-shirt");
            assertThat(data.get("name")).isEqualTo("Test T-Shirt");
        }

        @Test
        @DisplayName("404 — returns error when slug does not exist")
        void getBySlug_404() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/slug/nonexistent-slug-xyz"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            Map<?, ?> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("success")).isEqualTo(false);
        }
    }

    // ─── GET /api/products/{id} ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("200 — returns seeded product by id without auth")
        void getById_200() {
            // First retrieve all products to get a known id
            ResponseEntity<Map> listResponse = restTemplate.getForEntity(
                    baseUrl("/api/products"), Map.class);
            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Use the slug endpoint to get the seeded product id
            ResponseEntity<Map> slugResponse = restTemplate.getForEntity(
                    baseUrl("/api/products/slug/test-t-shirt"), Map.class);
            Map<?, ?> data = (Map<?, ?>) slugResponse.getBody().get("data");
            Integer productId = (Integer) data.get("id");

            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/" + productId), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            Map<?, ?> product = (Map<?, ?>) body.get("data");
            assertThat(product.get("id")).isEqualTo(productId);
            assertThat(product.get("name")).isEqualTo("Test T-Shirt");
            assertThat(product.get("active")).isEqualTo(true);
        }

        @Test
        @DisplayName("404 — returns error for nonexistent product id")
        void getById_404() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/999999"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(false);
        }
    }

    // ─── GET /api/products/featured ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/featured")
    class GetFeaturedProducts {

        @Test
        @DisplayName("200 — returns paginated featured products without auth")
        void getFeatured_200() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/featured"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success")).isEqualTo(true);
        }
    }

    // ─── GET /api/products/new-arrivals ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/new-arrivals")
    class GetNewArrivals {

        @Test
        @DisplayName("200 — returns list of new arrivals without auth")
        void getNewArrivals_200() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/new-arrivals"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("data")).isNotNull();
        }
    }

    // ─── GET /api/products/search ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/search")
    class SearchProducts {

        @Test
        @DisplayName("200 — returns matching products for query without auth")
        void search_200() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/search?q=shirt"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("content")).isNotNull();
        }

        @Test
        @DisplayName("200 — returns empty page for query with no matches")
        void search_200_noMatches() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/products/search?q=xyznotexist"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── POST /api/products ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/products")
    class CreateProduct {

        @Test
        @DisplayName("201 — admin can create a product")
        void create_201_asAdmin() {
            ProductRequest req = validProductRequest("IT Created Product");
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("message")).isEqualTo("Product created");
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("name")).isEqualTo("IT Created Product");
            assertThat(data.get("id")).isNotNull();
        }

        @Test
        @DisplayName("401 — unauthenticated request is rejected")
        void create_401_noToken() {
            ProductRequest req = validProductRequest("Unauthorized Product");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer role cannot create a product")
        void create_403_asCustomer() {
            ProductRequest req = validProductRequest("Forbidden Product");
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, authHeaders(customerToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("400 — returns validation error when name is missing")
        void create_400_missingName() {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                    Map.of("price", 29.99), authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("400 — returns validation error when price is negative")
        void create_400_negativePrice() {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                    Map.of("name", "Bad Product", "price", -10.00), authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ─── PUT /api/products/{id} ───────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProduct {

        private Integer getSeededProductId() {
            ResponseEntity<Map> slugResponse = restTemplate.getForEntity(
                    baseUrl("/api/products/slug/test-jeans"), Map.class);
            Map<?, ?> data = (Map<?, ?>) slugResponse.getBody().get("data");
            return (Integer) data.get("id");
        }

        @Test
        @DisplayName("200 — admin can update an existing product")
        void update_200_asAdmin() {
            Integer productId = getSeededProductId();
            ProductRequest req = new ProductRequest(
                    "Test Jeans Updated", "Updated description",
                    new BigDecimal("65.00"), null, null, 25,
                    null, List.of(), List.of(), false, true, List.of());

            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/" + productId), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("message")).isEqualTo("Product updated");
        }

        @Test
        @DisplayName("401 — unauthenticated update is rejected")
        void update_401_noToken() {
            ProductRequest req = validProductRequest("No Auth");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/1"), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer cannot update a product")
        void update_403_asCustomer() {
            Integer productId = getSeededProductId();
            ProductRequest req = validProductRequest("Customer Update");
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, authHeaders(customerToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/" + productId), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("404 — returns error when product does not exist")
        void update_404() {
            ProductRequest req = validProductRequest("Ghost Product");
            HttpEntity<ProductRequest> entity = new HttpEntity<>(req, authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/999999"), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("success")).isEqualTo(false);
        }
    }

    // ─── DELETE /api/products/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("200 — admin can delete a product")
        void delete_200_asAdmin() {
            // Create a dedicated product to delete
            ProductRequest req = validProductRequest("Product To Delete IT");
            HttpEntity<ProductRequest> createEntity = new HttpEntity<>(req, authHeaders(adminToken));
            ResponseEntity<Map> createResponse = restTemplate.exchange(
                    baseUrl("/api/products"), HttpMethod.POST, createEntity, Map.class);
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Integer productId = (Integer) ((Map<?, ?>) createResponse.getBody().get("data")).get("id");

            // Now delete it
            HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(adminToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/" + productId), HttpMethod.DELETE, deleteEntity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success")).isEqualTo(true);
            assertThat(response.getBody().get("message")).isEqualTo("Product deleted");
        }

        @Test
        @DisplayName("401 — unauthenticated delete is rejected")
        void delete_401_noToken() {
            HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/1"), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer cannot delete a product")
        void delete_403_asCustomer() {
            ResponseEntity<Map> slugResponse = restTemplate.getForEntity(
                    baseUrl("/api/products/slug/test-sneakers"), Map.class);
            Integer productId = (Integer) ((Map<?, ?>) slugResponse.getBody().get("data")).get("id");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/" + productId), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("404 — returns error when product to delete does not exist")
        void delete_404() {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/products/999999"), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("success")).isEqualTo(false);
        }
    }
}
