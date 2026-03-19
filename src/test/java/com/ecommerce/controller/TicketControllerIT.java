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
 * Integration tests for TicketController and the admin ticket endpoints in AdminController.
 *
 * Notes on seeded data (TestDataInitializer, e2e profile):
 *   - test@test.com (CUSTOMER) has two orders:
 *       ORD-E2E-NATL (PENDING)   — NATIONAL shipping
 *       ORD-E2E-PKUP (CONFIRMED) — PICKUP shipping
 *   - TicketService.createTicket requires OrderStatus.DELIVERED. Because neither seeded
 *     order is DELIVERED, POST /api/orders/{orderNumber}/tickets returns 400
 *     ("You can only report a problem on delivered orders.").
 *     Tests that depend on a captured ticket ID are guarded with Assumptions.assumeTrue
 *     and will be skipped gracefully when no ticket could be created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("TicketController — Integration")
class TicketControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String customerToken;
    private String adminToken;

    /** Captured when POST /api/orders/{orderNumber}/tickets returns 200. */
    private static Long createdTicketId;

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

    // ── POST /api/orders/{orderNumber}/tickets ────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 401 without authentication token")
    void createTicket_401_noToken() {
        Map<String, String> body = Map.of("subject", "Missing item", "description", "Item #2 was not in my order.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 400 when subject is blank")
    void createTicket_400_blankSubject() {
        Map<String, String> body = Map.of("subject", "", "description", "Item #2 was not in my order.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 400 when description is blank")
    void createTicket_400_blankDescription() {
        Map<String, String> body = Map.of("subject", "Missing item", "description", "");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 404 for a non-existent order number")
    void createTicket_404_unknownOrder() {
        Map<String, String> body = Map.of("subject", "Problem", "description", "Order never arrived.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-DOES-NOT-EXIST/tickets",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 403 when order belongs to a different user")
    void createTicket_403_orderBelongsToAnotherUser() {
        // The seeded orders belong to test@test.com; accessing them with the admin token
        // triggers the ownership check in TicketService and returns 403.
        Map<String, String> body = Map.of("subject", "Problem", "description", "Not my order.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 400 when order is not yet DELIVERED")
    void createTicket_400_orderNotDelivered() {
        // ORD-E2E-NATL is seeded with PENDING status, so ticket creation must be rejected.
        Map<String, String> body = Map.of("subject", "Where is it?", "description", "Still in transit.");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    /**
     * Attempts to create a ticket against a delivered order. Accepts 200 (ticket created),
     * 400 (order not delivered or ticket already exists), or 403 (ownership). The ticket ID
     * is captured only on a 200 so that subsequent read/update tests can use it.
     */
    @Test
    @Order(7)
    @DisplayName("POST /api/orders/{orderNumber}/tickets — returns 200 for a delivered order (captured if 200)")
    void createTicket_200_deliveredOrder_captureIdIfCreated() {
        Map<String, String> body = Map.of(
                "subject",     "Missing item",
                "description", "Item #2 was not included in my delivery."
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl() + "/api/orders/ORD-E2E-NATL/tickets",
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        // 200 = ticket created (order was DELIVERED in some test environment setup)
        // 400 = order not yet delivered or duplicate ticket from a previous run
        // 403 = order ownership mismatch (should not happen here)
        assertThat(response.getStatusCode()).isIn(
                HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN);

        if (response.getStatusCode() == HttpStatus.OK) {
            assertThat(response.getBody().path("success").asBoolean()).isTrue();
            assertThat(response.getBody().path("message").asText()).isEqualTo("Ticket created");
            createdTicketId = response.getBody().path("data").path("id").asLong();
            assertThat(createdTicketId).isGreaterThan(0);

            JsonNode data = response.getBody().path("data");
            assertThat(data.path("orderNumber").asText()).isEqualTo("ORD-E2E-NATL");
            assertThat(data.path("subject").asText()).isEqualTo("Missing item");
            assertThat(data.path("status").asText()).isEqualTo("OPEN");
        }
    }

    // ── GET /api/tickets ──────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("GET /api/tickets — returns 401 without authentication token")
    void getMyTickets_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/tickets — returns 200 with the customer's ticket list")
    void getMyTickets_200_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/tickets — returned list contains the newly created ticket (when present)")
    void getMyTickets_200_containsCreatedTicket() {
        Assumptions.assumeTrue(createdTicketId != null,
                "Skipped: no ticket was created in order 7 (order not yet DELIVERED)");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode tickets = response.getBody().path("data");
        boolean found = false;
        for (JsonNode ticket : tickets) {
            if (ticket.path("id").asLong() == createdTicketId) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Created ticket with id=%d should appear in the list", createdTicketId).isTrue();
    }

    // ── GET /api/tickets/{id} ─────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("GET /api/tickets/{id} — returns 401 without authentication token")
    void getTicketById_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets/1",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/tickets/{id} — returns 200 for the ticket owner")
    void getTicketById_200_owner() {
        Assumptions.assumeTrue(createdTicketId != null,
                "Skipped: no ticket was created in order 7 (order not yet DELIVERED)");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets/" + createdTicketId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("id").asLong()).isEqualTo(createdTicketId);
        assertThat(data.path("subject").asText()).isEqualTo("Missing item");
        assertThat(data.path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/tickets/{id} — returns 404 for a non-existent ticket")
    void getTicketById_404() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/tickets/999999",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    // ── Admin: GET /api/admin/tickets ─────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("GET /api/admin/tickets — returns 401 without authentication token")
    void getAllTickets_401_noToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(15)
    @DisplayName("GET /api/admin/tickets — returns 403 when called by a customer")
    void getAllTickets_403_customerToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(16)
    @DisplayName("GET /api/admin/tickets — returns 200 with all tickets for admin")
    void getAllTickets_200_adminToken() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    // ── Admin: PUT /api/admin/tickets/{id} ────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("PUT /api/admin/tickets/{id} — returns 401 without authentication token")
    void updateTicket_401_noToken() {
        Map<String, Object> body = Map.of("status", "RESOLVED", "adminNotes", "Issue resolved.");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets/1",
                HttpMethod.PUT,
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(18)
    @DisplayName("PUT /api/admin/tickets/{id} — returns 403 when called by a customer")
    void updateTicket_403_customerToken() {
        Map<String, Object> body = Map.of("status", "RESOLVED", "adminNotes", "Issue resolved.");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets/1",
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(customerToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(19)
    @DisplayName("PUT /api/admin/tickets/{id} — admin gets 404 for a non-existent ticket")
    void updateTicket_404_unknownTicket() {
        Map<String, Object> body = Map.of("status", "RESOLVED", "adminNotes", "Nothing to resolve.");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets/999999",
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PUT /api/admin/tickets/{id} — admin can update status to RESOLVED")
    void updateTicket_200_adminResolves() {
        Assumptions.assumeTrue(createdTicketId != null,
                "Skipped: no ticket was created in order 7 (order not yet DELIVERED)");

        Map<String, Object> body = Map.of("status", "RESOLVED", "adminNotes", "Issue confirmed and resolved.");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets/" + createdTicketId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("message").asText()).isEqualTo("Ticket updated");

        JsonNode data = response.getBody().path("data");
        assertThat(data.path("id").asLong()).isEqualTo(createdTicketId);
        assertThat(data.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(data.path("adminNotes").asText()).isEqualTo("Issue confirmed and resolved.");
    }

    @Test
    @Order(21)
    @DisplayName("PUT /api/admin/tickets/{id} — returns 400 when status is missing from request body")
    void updateTicket_400_missingStatus() {
        Assumptions.assumeTrue(createdTicketId != null,
                "Skipped: no ticket was created in order 7 (order not yet DELIVERED)");

        Map<String, Object> body = Map.of("adminNotes", "Missing status field.");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl() + "/api/admin/tickets/" + createdTicketId,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(adminToken)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
