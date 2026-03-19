package com.ecommerce.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@DisplayName("CategoryController — Integration")
class CategoryControllerIT {

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

    /** Creates a category via POST and returns its integer id. */
    private Integer createCategory(String name, String description) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("name", name, "description", description),
                authHeaders(adminToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/categories"), HttpMethod.POST, entity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Integer) ((Map<?, ?>) response.getBody().get("data")).get("id");
    }

    // ─── GET /api/categories ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/categories")
    class GetAllCategories {

        @Test
        @DisplayName("200 — public endpoint returns list of categories")
        void getAll_200_public() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/categories"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("data")).isNotNull();
            assertThat((List<?>) body.get("data")).isInstanceOf(List.class);
        }
    }

    // ─── GET /api/categories/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/categories/{id}")
    class GetCategoryById {

        @Test
        @DisplayName("200 — returns category by id without auth")
        void getById_200() {
            Integer id = createCategory("IT Category GetById", "For get-by-id test");

            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/categories/" + id), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("id")).isEqualTo(id);
            assertThat(data.get("name")).isEqualTo("IT Category GetById");
            assertThat(data.get("description")).isEqualTo("For get-by-id test");
            assertThat(data.get("slug")).isNotNull();
        }

        @Test
        @DisplayName("404 — returns error when category does not exist")
        void getById_404() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl("/api/categories/999999"), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(false);
        }
    }

    // ─── POST /api/categories ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/categories")
    class CreateCategory {

        @Test
        @DisplayName("201 — admin can create a category")
        void create_201_asAdmin() {
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "IT New Category", "description", "Integration test"),
                    authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("message")).isEqualTo("Category created");
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("name")).isEqualTo("IT New Category");
            assertThat(data.get("id")).isNotNull();
            assertThat(data.get("slug")).isNotNull();
        }

        @Test
        @DisplayName("201 — admin can create a category with only a name")
        void create_201_nameOnly() {
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "IT Name Only Category"),
                    authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
            assertThat(data.get("name")).isEqualTo("IT Name Only Category");
        }

        @Test
        @DisplayName("401 — unauthenticated request is rejected")
        void create_401_noToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "Unauthorized Category"), headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer role cannot create a category")
        void create_403_asCustomer() {
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "Forbidden Category"), authHeaders(customerToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories"), HttpMethod.POST, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ─── PUT /api/categories/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/categories/{id}")
    class UpdateCategory {

        @Test
        @DisplayName("200 — admin can update an existing category")
        void update_200_asAdmin() {
            Integer id = createCategory("IT Category Before Update", "Original description");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "IT Category After Update", "description", "Updated description"),
                    authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/" + id), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("message")).isEqualTo("Category updated");
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            assertThat(data.get("name")).isEqualTo("IT Category After Update");
        }

        @Test
        @DisplayName("401 — unauthenticated update is rejected")
        void update_401_noToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "No Auth Update"), headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/1"), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer cannot update a category")
        void update_403_asCustomer() {
            Integer id = createCategory("IT Category Customer Update Attempt", "desc");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "Customer Update Attempt"), authHeaders(customerToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/" + id), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("404 — returns error when category to update does not exist")
        void update_404() {
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("name", "Ghost Category"), authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/999999"), HttpMethod.PUT, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("success")).isEqualTo(false);
        }
    }

    // ─── DELETE /api/categories/{id} ──────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/categories/{id}")
    class DeleteCategory {

        @Test
        @DisplayName("200 — admin can delete a category")
        void delete_200_asAdmin() {
            Integer id = createCategory("IT Category To Delete", "will be deleted");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/" + id), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = response.getBody();
            assertThat(body.get("success")).isEqualTo(true);
            assertThat(body.get("message")).isEqualTo("Category deleted");

            // Verify it is truly gone
            ResponseEntity<Map> getAfterDelete = restTemplate.getForEntity(
                    baseUrl("/api/categories/" + id), Map.class);
            assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("401 — unauthenticated delete is rejected")
        void delete_401_noToken() {
            HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/1"), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("403 — customer cannot delete a category")
        void delete_403_asCustomer() {
            Integer id = createCategory("IT Category Customer Delete Attempt", "desc");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/" + id), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("404 — returns error when category to delete does not exist")
        void delete_404() {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl("/api/categories/999999"), HttpMethod.DELETE, entity, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("success")).isEqualTo(false);
        }
    }
}
