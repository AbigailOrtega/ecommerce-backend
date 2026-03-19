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
@DisplayName("PaymentController — Integration")
class PaymentControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String customerToken;

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        customerToken = obtainToken("test@test.com", "Test123!");
    }

    private String obtainToken(String email, String password) {
        Map<String, String> credentials = Map.of("email", email, "password", password);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"),
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

    // ── GET /api/payments/stripe/config ───────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/payments/stripe/config — returns 200 without authentication (public endpoint)")
    void getStripeConfig_200_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl("/api/payments/stripe/config"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        // publishableKey key must be present (may be empty if Stripe not configured in e2e)
        assertThat(response.getBody().path("data").has("publishableKey")).isTrue();
    }

    // ── GET /api/payments/paypal/config ───────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("GET /api/payments/paypal/config — returns 200 without authentication (public endpoint)")
    void getPayPalConfig_200_noAuth() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl("/api/payments/paypal/config"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        // clientId key must be present (may be empty if PayPal not configured in e2e)
        assertThat(response.getBody().path("data").has("clientId")).isTrue();
    }

    // ── POST /api/payments/stripe/create-intent ───────────────────────────────

    @Test
    @Order(3)
    @DisplayName("POST /api/payments/stripe/create-intent — returns 401 without authentication")
    void createStripeIntent_401_noAuth() {
        Map<String, Object> body = Map.of("amount", 49.99);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/stripe/create-intent"),
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/payments/stripe/create-intent — auth layer passed with valid customer token (may be 400/500 if Stripe not configured)")
    void createStripeIntent_authPassed_withCustomerToken() {
        Map<String, Object> body = Map.of("amount", 49.99, "currency", "usd");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/stripe/create-intent"),
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        // Must NOT be a 401 (Unauthorized) or 403 (Forbidden) —
        // the request got past the security layer. Stripe may reject it
        // with 400 or 500 if the secret key is not set in the e2e profile.
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/payments/paypal/create-order ────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("POST /api/payments/paypal/create-order — returns 401 without authentication")
    void createPayPalOrder_401_noAuth() {
        Map<String, Object> body = Map.of("amount", 99.99);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/create-order"),
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/payments/paypal/create-order — auth layer passed with valid customer token (may be 400/500 if PayPal not configured)")
    void createPayPalOrder_authPassed_withCustomerToken() {
        Map<String, Object> body = Map.of("amount", 99.99);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/create-order"),
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        // Must NOT be 401 / 403 — the request got past security.
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/payments/paypal/capture-order ───────────────────────────────

    @Test
    @Order(7)
    @DisplayName("POST /api/payments/paypal/capture-order — returns 401 without authentication")
    void capturePayPalOrder_401_noAuth() {
        Map<String, Object> body = Map.of("orderId", "PAYPAL-TEST-ORDER-001");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/capture-order"),
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(8)
    @DisplayName("POST /api/payments/paypal/capture-order — auth layer passed with valid customer token (may be 400/500 if PayPal not configured)")
    void capturePayPalOrder_authPassed_withCustomerToken() {
        Map<String, Object> body = Map.of("orderId", "PAYPAL-TEST-ORDER-001");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/capture-order"),
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/payments/paypal/confirm-payment ─────────────────────────────

    @Test
    @Order(9)
    @DisplayName("POST /api/payments/paypal/confirm-payment — returns 401 without authentication")
    void confirmPayPalPayment_401_noAuth() {
        Map<String, String> body = Map.of("orderNumber", "ORD-TEST-001", "captureId", "CAP-TEST-001");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/confirm-payment"),
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(10)
    @DisplayName("POST /api/payments/paypal/confirm-payment — auth layer passed with valid customer token (may be 400/500 if order not found)")
    void confirmPayPalPayment_authPassed_withCustomerToken() {
        Map<String, String> body = Map.of("orderNumber", "ORD-NON-EXISTENT", "captureId", "CAP-TEST-001");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl("/api/payments/paypal/confirm-payment"),
                new HttpEntity<>(body, authHeaders()),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
