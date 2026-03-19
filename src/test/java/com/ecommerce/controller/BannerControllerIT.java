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
 * Integration tests for BannerController (public) and the banner management
 * endpoints in AdminController.
 *
 * Test order:
 *   1.  GET /api/banner/active              — public, no auth needed       → 200
 *   2.  POST /api/admin/banners             — no auth                      → 401
 *   3.  POST /api/admin/banners             — customer token               → 403
 *   4.  POST /api/admin/banners             — admin token, captures ID     → 200
 *   5.  GET  /api/banner/active             — created banner is present    → 200
 *   6.  GET  /api/admin/banners             — admin list includes banner   → 200
 *   7.  PUT  /api/admin/banners/{id}        — admin updates banner         → 200
 *   8.  PUT  /api/admin/banners/{id}        — missing imageUrl             → 400
 *   9.  PATCH /api/admin/banners/{id}/toggle — admin toggles inactive      → 200
 *   10. GET  /api/banner/active             — toggled-off banner absent    → 200
 *   11. DELETE /api/admin/banners/{id}      — no auth                      → 401
 *   12. DELETE /api/admin/banners/{id}      — customer token               → 403
 *   13. DELETE /api/admin/banners/{id}      — admin deletes                → 200
 *   14. DELETE /api/admin/banners/{id}      — already deleted              → 404
 *   15. GET  /api/admin/banners/{id}        — not found after delete       → 404 (list check)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("BannerController — Integration")
class BannerControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String adminToken;
    private String customerToken;

    /** Shared across ordered tests: the ID of the banner created in order 4. */
    private static Long createdBannerId;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        adminToken    = obtainToken("admin@test.com", "Admin123!");
        customerToken = obtainToken("test@test.com",  "Test123!");
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

    // ── GET /api/banner/active (public) ───────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/banner/active — returns 200 without authentication")
    void getActiveBanners_200_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/banner/active",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    // ── POST /api/admin/banners ────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("POST /api/admin/banners — returns 401 without authentication token")
    void createBanner_401_noToken() {
        Map<String, String> body = Map.of("imageUrl", "https://img.example.com/it-banner.jpg");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/admin/banners",
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/admin/banners — returns 403 for CUSTOMER role")
    void createBanner_403_customerToken() {
        Map<String, String> body = Map.of("imageUrl", "https://img.example.com/it-banner.jpg");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/admin/banners",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/admin/banners — returns 200 and creates banner for ADMIN")
    void createBanner_200_adminToken() {
        Map<String, String> body = Map.of(
                "imageUrl", "https://img.example.com/it-banner.jpg",
                "linkUrl",  "/it-sale"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/admin/banners",
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("imageUrl").asText()).isEqualTo("https://img.example.com/it-banner.jpg");
        assertThat(data.path("linkUrl").asText()).isEqualTo("/it-sale");
        assertThat(data.path("active").asBoolean()).isTrue();

        createdBannerId = data.path("id").asLong();
        assertThat(createdBannerId).isGreaterThan(0);
    }

    // ── GET /api/banner/active after creation ─────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("GET /api/banner/active — includes newly created banner")
    void getActiveBanners_200_includesCreatedBanner() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/banner/active",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode banners = response.getBody().path("data");
        boolean found = false;
        for (JsonNode banner : banners) {
            if (createdBannerId.equals(banner.path("id").asLong())) {
                found = true;
                assertThat(banner.path("active").asBoolean()).isTrue();
                break;
            }
        }
        assertThat(found).as("Created banner (id=%d) should appear in active list", createdBannerId).isTrue();
    }

    // ── GET /api/admin/banners ─────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("GET /api/admin/banners — returns 200 with banner list for ADMIN")
    void getAllBanners_200_adminToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();

        JsonNode banners = response.getBody().path("data");
        boolean found = false;
        for (JsonNode banner : banners) {
            if (createdBannerId.equals(banner.path("id").asLong())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Admin list should contain banner id=%d", createdBannerId).isTrue();
    }

    // ── PUT /api/admin/banners/{id} ────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("PUT /api/admin/banners/{id} — returns 200 and updates banner for ADMIN")
    void updateBanner_200_adminToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        Map<String, String> body = Map.of(
                "imageUrl", "https://img.example.com/it-banner-updated.jpg",
                "linkUrl",  "/it-sale-updated"
        );

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("imageUrl").asText()).isEqualTo("https://img.example.com/it-banner-updated.jpg");
        assertThat(data.path("linkUrl").asText()).isEqualTo("/it-sale-updated");
    }

    @Test
    @Order(8)
    @DisplayName("PUT /api/admin/banners/{id} — returns 400 when imageUrl is blank")
    void updateBanner_400_missingImageUrl() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        Map<String, String> body = Map.of("imageUrl", "");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── PATCH /api/admin/banners/{id}/toggle ──────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("PATCH /api/admin/banners/{id}/toggle — returns 200 and toggles banner to inactive")
    void toggleBanner_200_adminToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId + "/toggle",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        // Banner was active; toggle flips it to inactive
        assertThat(response.getBody().path("data").path("active").asBoolean()).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/banner/active — toggled-off banner is no longer in the active list")
    void getActiveBanners_200_excludesInactiveBanner() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner toggled in order 9");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/banner/active",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode banners = response.getBody().path("data");
        for (JsonNode banner : banners) {
            assertThat(banner.path("id").asLong())
                    .as("Inactive banner (id=%d) must not appear in active list", createdBannerId)
                    .isNotEqualTo(createdBannerId);
        }
    }

    // ── DELETE /api/admin/banners/{id} ────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("DELETE /api/admin/banners/{id} — returns 401 without authentication token")
    void deleteBanner_401_noToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(12)
    @DisplayName("DELETE /api/admin/banners/{id} — returns 403 for CUSTOMER role")
    void deleteBanner_403_customerToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(13)
    @DisplayName("DELETE /api/admin/banners/{id} — returns 200 and deletes banner for ADMIN")
    void deleteBanner_200_adminToken() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner created in order 4");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
    }

    @Test
    @Order(14)
    @DisplayName("DELETE /api/admin/banners/{id} — returns 404 for already-deleted banner")
    void deleteBanner_404_alreadyDeleted() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner deleted in order 13");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners/" + createdBannerId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(15)
    @DisplayName("GET /api/admin/banners — deleted banner is no longer present in admin list")
    void getAllBanners_200_deletedBannerAbsent() {
        Assumptions.assumeTrue(createdBannerId != null, "Requires banner deleted in order 13");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/banners",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode banners = response.getBody().path("data");
        for (JsonNode banner : banners) {
            assertThat(banner.path("id").asLong())
                    .as("Deleted banner (id=%d) must not appear in admin list", createdBannerId)
                    .isNotEqualTo(createdBannerId);
        }
    }
}
