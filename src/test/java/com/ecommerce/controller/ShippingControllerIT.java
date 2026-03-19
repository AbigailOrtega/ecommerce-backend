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
 * Integration tests for ShippingController (public endpoints) and the shipping
 * management endpoints in AdminController.
 *
 * Public endpoints (ShippingController, /api/shipping/**):
 *   - GET  /api/shipping/config
 *   - GET  /api/shipping/pickup-locations
 *   - POST /api/shipping/calculate-national
 *
 * Admin-only endpoints (AdminController, /api/admin/**):
 *   - GET  /api/admin/shipping/config
 *   - PUT  /api/admin/shipping/config
 *   - GET  /api/admin/pickup-locations
 *   - POST /api/admin/pickup-locations
 *   - PUT  /api/admin/pickup-locations/{id}
 *   - PATCH /api/admin/pickup-locations/{id}/toggle
 *   - POST /api/admin/pickup-locations/{id}/time-slots
 *   - DELETE /api/admin/pickup-locations/{id}
 *
 * Test order:
 *   1.  GET  /api/shipping/config                         — public           → 200
 *   2.  GET  /api/shipping/pickup-locations               — public           → 200
 *   3.  POST /api/shipping/calculate-national             — public, body     → 200
 *   4.  POST /api/shipping/calculate-national             — empty body       → 200
 *   5.  GET  /api/admin/shipping/config                   — no auth          → 401
 *   6.  GET  /api/admin/shipping/config                   — customer         → 403
 *   7.  GET  /api/admin/shipping/config                   — admin            → 200
 *   8.  PUT  /api/admin/shipping/config                   — admin updates    → 200
 *   9.  GET  /api/admin/pickup-locations                  — no auth          → 401
 *   10. GET  /api/admin/pickup-locations                  — customer         → 403
 *   11. GET  /api/admin/pickup-locations                  — admin            → 200
 *   12. POST /api/admin/pickup-locations                  — admin creates    → 200
 *   13. PUT  /api/admin/pickup-locations/{id}             — admin updates    → 200
 *   14. PATCH /api/admin/pickup-locations/{id}/toggle     — admin toggles    → 200
 *   15. GET  /api/shipping/pickup-locations               — toggled location absent → 200
 *   16. POST /api/admin/pickup-locations/{id}/time-slots  — admin adds slot  → 200
 *   17. DELETE /api/admin/pickup-locations/{id}           — no auth          → 401
 *   18. DELETE /api/admin/pickup-locations/{id}           — customer         → 403
 *   19. DELETE /api/admin/pickup-locations/{id}           — admin deletes    → 200
 *   20. DELETE /api/admin/pickup-locations/{id}           — already deleted  → 404
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ShippingController — Integration")
class ShippingControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String adminToken;
    private String customerToken;

    /** Shared across ordered tests: the ID of the pickup location created in order 12. */
    private static Long createdLocationId;

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

    // ── GET /api/shipping/config (public) ─────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/shipping/config — returns 200 without authentication")
    void getShippingConfig_200_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/shipping/config",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.has("nationalEnabled")).isTrue();
        assertThat(data.has("pickupEnabled")).isTrue();
    }

    // ── GET /api/shipping/pickup-locations (public) ───────────────────────────

    @Test
    @Order(2)
    @DisplayName("GET /api/shipping/pickup-locations — returns 200 without authentication")
    void getPickupLocations_200_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/shipping/pickup-locations",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    // ── POST /api/shipping/calculate-national (public) ────────────────────────

    @Test
    @Order(3)
    @DisplayName("POST /api/shipping/calculate-national — returns 200 with shipping cost")
    void calculateNational_200_withAddress() {
        Map<String, String> body = Map.of(
                "address", "Calle Falsa 123",
                "city",    "Ciudad de México",
                "state",   "CDMX",
                "zipCode", "06600",
                "country", "MX"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/shipping/calculate-national",
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.has("skydropxAvailable")).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/shipping/calculate-national — returns 200 with empty body (null fields handled by service)")
    void calculateNational_200_emptyBody() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/shipping/calculate-national",
                new HttpEntity<>(Map.of(), jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").has("skydropxAvailable")).isTrue();
    }

    // ── GET /api/admin/shipping/config ────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("GET /api/admin/shipping/config — returns 401 without authentication token")
    void getAdminShippingConfig_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/shipping/config",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/admin/shipping/config — returns 403 for CUSTOMER role")
    void getAdminShippingConfig_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/shipping/config",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/admin/shipping/config — returns 200 with admin config for ADMIN")
    void getAdminShippingConfig_200_adminToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/shipping/config",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.has("nationalEnabled")).isTrue();
        assertThat(data.has("pickupEnabled")).isTrue();
        assertThat(data.has("hasSkydropxCredentials")).isTrue();
    }

    // ── PUT /api/admin/shipping/config ────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("PUT /api/admin/shipping/config — returns 200 and updates config for ADMIN")
    void updateShippingConfig_200_adminToken() {
        Map<String, Object> body = Map.of(
                "nationalEnabled",   true,
                "pickupEnabled",     true,
                "nationalBasePrice", 99,
                "whatsappNumber",    "5215599998888"
        );

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/shipping/config",
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("nationalEnabled").asBoolean()).isTrue();
        assertThat(data.path("pickupEnabled").asBoolean()).isTrue();
    }

    // ── GET /api/admin/pickup-locations ───────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("GET /api/admin/pickup-locations — returns 401 without authentication token")
    void getAdminPickupLocations_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/admin/pickup-locations — returns 403 for CUSTOMER role")
    void getAdminPickupLocations_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/admin/pickup-locations — returns 200 with all locations for ADMIN")
    void getAdminPickupLocations_200_adminToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    // ── POST /api/admin/pickup-locations ──────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("POST /api/admin/pickup-locations — returns 200 and creates location for ADMIN")
    void createPickupLocation_200_adminToken() {
        Map<String, String> body = Map.of(
                "name",    "Sucursal IT Test",
                "address", "Av. Prueba 42",
                "city",    "Guadalajara",
                "state",   "Jalisco"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/admin/pickup-locations",
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("name").asText()).isEqualTo("Sucursal IT Test");
        assertThat(data.path("city").asText()).isEqualTo("Guadalajara");
        assertThat(data.path("active").asBoolean()).isTrue();

        createdLocationId = data.path("id").asLong();
        assertThat(createdLocationId).isGreaterThan(0);
    }

    // ── PUT /api/admin/pickup-locations/{id} ──────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("PUT /api/admin/pickup-locations/{id} — returns 200 and updates location for ADMIN")
    void updatePickupLocation_200_adminToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        Map<String, String> body = Map.of(
                "name",    "Sucursal IT Test (Updated)",
                "address", "Av. Prueba 99",
                "city",    "Guadalajara",
                "state",   "Jalisco"
        );

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("name").asText())
                .isEqualTo("Sucursal IT Test (Updated)");
    }

    // ── PATCH /api/admin/pickup-locations/{id}/toggle ─────────────────────────

    @Test
    @Order(14)
    @DisplayName("PATCH /api/admin/pickup-locations/{id}/toggle — returns 200 and toggles location to inactive")
    void togglePickupLocation_200_adminToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId + "/toggle",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        // Location was active; toggle flips it to inactive
        assertThat(response.getBody().path("data").path("active").asBoolean()).isFalse();
    }

    @Test
    @Order(15)
    @DisplayName("GET /api/shipping/pickup-locations — toggled-off location is absent from public list")
    void getPickupLocations_200_excludesInactiveLocation() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location toggled in order 14");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/shipping/pickup-locations",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode locations = response.getBody().path("data");
        for (JsonNode location : locations) {
            assertThat(location.path("id").asLong())
                    .as("Inactive location (id=%d) must not appear in public list", createdLocationId)
                    .isNotEqualTo(createdLocationId);
        }
    }

    // ── POST /api/admin/pickup-locations/{id}/time-slots ─────────────────────

    @Test
    @Order(16)
    @DisplayName("POST /api/admin/pickup-locations/{id}/time-slots — returns 200 and adds time slot")
    void addTimeSlot_200_adminToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        Map<String, String> body = Map.of("label", "Lunes 09:00 – 13:00");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId + "/time-slots",
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("label").asText())
                .isEqualTo("Lunes 09:00 – 13:00");
    }

    // ── DELETE /api/admin/pickup-locations/{id} ───────────────────────────────

    @Test
    @Order(17)
    @DisplayName("DELETE /api/admin/pickup-locations/{id} — returns 401 without authentication token")
    void deletePickupLocation_401_noToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(18)
    @DisplayName("DELETE /api/admin/pickup-locations/{id} — returns 403 for CUSTOMER role")
    void deletePickupLocation_403_customerToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(19)
    @DisplayName("DELETE /api/admin/pickup-locations/{id} — returns 200 and deletes location for ADMIN")
    void deletePickupLocation_200_adminToken() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location created in order 12");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("DELETE /api/admin/pickup-locations/{id} — returns 404 for already-deleted location")
    void deletePickupLocation_404_alreadyDeleted() {
        Assumptions.assumeTrue(createdLocationId != null, "Requires location deleted in order 19");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/pickup-locations/" + createdLocationId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
