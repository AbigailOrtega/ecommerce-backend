package com.ecommerce.service;

import com.ecommerce.service.PayPalClient.CaptureSnapshot;
import com.ecommerce.service.PayPalClient.PayPalApiException;
import com.ecommerce.service.PaymentService.PayPalOrderSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("PayPalClient")
class PayPalClientTest {

    private static final String TOKEN_JSON = "{\"access_token\":\"A21-token\",\"expires_in\":32400}";

    private MockRestServiceServer server;
    private PayPalClient client;
    private MutableClock clock;

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PayPalClient.SANDBOX_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        client = new PayPalClient(builder.build(), "client-id", "secret", clock);
    }

    private void expectToken() {
        String basic = Base64.getEncoder().encodeToString("client-id:secret".getBytes());
        server.expect(once(), requestTo(PayPalClient.SANDBOX_URL + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("is configured only with both client id and secret")
    void isConfigured() {
        assertThat(client.isConfigured()).isTrue();
        assertThat(new PayPalClient(RestClient.create(), "", "secret", clock).isConfigured()).isFalse();
        assertThat(new PayPalClient(RestClient.create(), "id", null, clock).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("creates a CAPTURE order in MXN with the amount rounded to cents")
    void createOrder() throws Exception {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer A21-token"))
                .andExpect(header("Prefer", "return=representation"))
                .andExpect(jsonPath("$.intent").value("CAPTURE"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("MXN"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("150.51"))
                .andRespond(withSuccess("""
                        {"id":"5O190127TN364715T","status":"CREATED",
                         "purchase_units":[{"amount":{"currency_code":"MXN","value":"150.51"}}]}""",
                        MediaType.APPLICATION_JSON));

        PayPalOrderSnapshot order = client.createOrder(new BigDecimal("150.505"));

        assertThat(order.id()).isEqualTo("5O190127TN364715T");
        assertThat(order.status()).isEqualTo("CREATED");
        assertThat(order.amount()).isEqualByComparingTo("150.51");
        server.verify();
    }

    @Test
    @DisplayName("reuses the OAuth token until shortly before it expires")
    void tokenIsCached() throws Exception {
        expectToken();
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/ORDER-1"))
                    .andRespond(withSuccess("{\"id\":\"ORDER-1\",\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        }
        client.getOrder("ORDER-1");
        client.getOrder("ORDER-1");
        server.verify();

        // 9 hours later the token is about to expire: a new one is requested
        server.reset();
        clock.now = clock.now.plusSeconds(32400 - 60);
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/ORDER-1"))
                .andRespond(withSuccess("{\"id\":\"ORDER-1\",\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        client.getOrder("ORDER-1");
        server.verify();
    }

    @Test
    @DisplayName("an unknown order id is empty, not an error")
    void getOrder_notFound() throws Exception {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/CAPTURE-ID"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"name\":\"RESOURCE_NOT_FOUND\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.getOrder("CAPTURE-ID")).isEmpty();
    }

    @Test
    @DisplayName("capture reads the capture id, status and captured amount, and is idempotent per order")
    void captureOrder() throws Exception {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/ORDER-2/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "capture-ORDER-2"))
                .andRespond(withSuccess("""
                        {"id":"ORDER-2","status":"COMPLETED","purchase_units":[{
                          "amount":{"currency_code":"MXN","value":"99.00"},
                          "payments":{"captures":[{"id":"CAP-7","status":"COMPLETED",
                                                   "amount":{"currency_code":"MXN","value":"99.00"}}]}}]}""",
                        MediaType.APPLICATION_JSON));

        PayPalOrderSnapshot order = client.captureOrder("ORDER-2");

        assertThat(order.status()).isEqualTo("COMPLETED");
        assertThat(order.captureId()).isEqualTo("CAP-7");
        assertThat(order.captureStatus()).isEqualTo("COMPLETED");
        assertThat(order.currency()).isEqualTo("MXN");
        assertThat(order.amount()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("a declined capture surfaces PayPal's reason as PayPalApiException")
    void captureOrder_declined() {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/ORDER-3/capture"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"INSTRUMENT_DECLINED\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.captureOrder("ORDER-3"))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("INSTRUMENT_DECLINED");
    }

    @Test
    @DisplayName("a server error or an unreachable PayPal is a PayPalApiException")
    void serverError() {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/checkout/orders/ORDER-4"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getOrder("ORDER-4")).isInstanceOf(PayPalApiException.class);
    }

    @Test
    @DisplayName("bad credentials are a PayPalApiException")
    void badCredentials() {
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v1/oauth2/token"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.getOrder("ORDER-5")).isInstanceOf(PayPalApiException.class);
    }

    @Test
    @DisplayName("reads a capture; an unknown capture is empty")
    void getCapture() throws Exception {
        expectToken();
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/payments/captures/CAP-1"))
                .andRespond(withSuccess("{\"id\":\"CAP-1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"MXN\",\"value\":\"150.00\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(PayPalClient.SANDBOX_URL + "/v2/payments/captures/CAP-X"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<CaptureSnapshot> capture = client.getCapture("CAP-1");
        assertThat(capture).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("COMPLETED");
            assertThat(c.currency()).isEqualTo("MXN");
            assertThat(c.amount()).isEqualByComparingTo("150.00");
        });
        assertThat(client.getCapture("CAP-X")).isEmpty();
    }
}
