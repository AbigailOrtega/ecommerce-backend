package com.ecommerce.controller;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("StripeWebhookController — Integration")
class StripeWebhookControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    // ── Constant test values ──────────────────────────────────────────────────

    private static final String MINIMAL_PAYLOAD =
            "{\"id\":\"evt_test_it_001\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\","
            + "\"data\":{\"object\":{\"id\":\"pi_test_it_001\",\"object\":\"payment_intent\"}}}";

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders plainTextHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return headers;
    }

    // ── POST /api/webhooks/stripe ─────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/webhooks/stripe — returns 400 when Stripe-Signature header is missing")
    void webhook_400_missingSignatureHeader() {
        // The handler declares @RequestHeader("Stripe-Signature") as required=true.
        // Spring resolves a missing required header to 400 before the method body runs.
        HttpEntity<String> request = new HttpEntity<>(MINIMAL_PAYLOAD, plainTextHeaders());

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/api/webhooks/stripe"),
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/webhooks/stripe — returns 400 when Stripe-Signature header is invalid")
    void webhook_400_invalidSignatureHeader() {
        HttpHeaders headers = plainTextHeaders();
        headers.set("Stripe-Signature", "invalid-signature-value");

        HttpEntity<String> request = new HttpEntity<>(MINIMAL_PAYLOAD, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/api/webhooks/stripe"),
                request,
                String.class
        );

        // The real Stripe SDK will throw SignatureVerificationException for an invalid
        // signature, which the controller maps to 400.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/webhooks/stripe — returns 400 with well-formed but bad Stripe-Signature")
    void webhook_400_wellFormedButBadSignature() {
        // This mirrors the Stripe signature format (t=timestamp,v1=hash) but with
        // wrong values — the SDK will still reject it.
        HttpHeaders headers = plainTextHeaders();
        headers.set("Stripe-Signature", "t=1700000000,v1=badhash000000000000000000000000000000000000000000000000000000000000");

        HttpEntity<String> request = new HttpEntity<>(MINIMAL_PAYLOAD, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/api/webhooks/stripe"),
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/webhooks/stripe — endpoint is publicly accessible (no 401/403 for unauthenticated requests)")
    void webhook_publicEndpoint_noAuthRequired() {
        // The security config permits POST /api/webhooks/stripe without authentication.
        // Even a missing-signature request must not return 401 or 403.
        HttpEntity<String> request = new HttpEntity<>(MINIMAL_PAYLOAD, plainTextHeaders());

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/api/webhooks/stripe"),
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
