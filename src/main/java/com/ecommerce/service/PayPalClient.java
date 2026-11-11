package com.ecommerce.service;

import com.ecommerce.service.PaymentService.PayPalOrderSnapshot;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PayPal Orders v2 / Payments v2 over plain HTTPS. Replaces the deprecated checkout-sdk.
 * Only the handful of calls the store makes: create, get and capture an order, and get a capture.
 *
 * @see <a href="https://developer.paypal.com/docs/api/orders/v2/">Orders v2</a>
 */
@Slf4j
@Component
public class PayPalClient {

    static final String SANDBOX_URL = "https://api-m.sandbox.paypal.com";
    static final String LIVE_URL = "https://api-m.paypal.com";

    /** Renew the OAuth token this long before PayPal says it expires. */
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    /** Thrown when PayPal cannot be reached or refuses the call (e.g. INSTRUMENT_DECLINED on capture). */
    public static class PayPalApiException extends IOException {
        PayPalApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record CaptureSnapshot(String id, String status, String currency, BigDecimal amount) {}

    @Autowired
    public PayPalClient(RestClient.Builder builder,
                        @Value("${app.paypal.client-id:}") String clientId,
                        @Value("${app.paypal.client-secret:}") String clientSecret,
                        @Value("${app.paypal.mode:sandbox}") String mode) {
        this(builder
                .baseUrl("live".equalsIgnoreCase(mode) ? LIVE_URL : SANDBOX_URL)
                .requestFactory(timeouts())
                .build(), clientId, clientSecret, Clock.systemUTC());
        if (isConfigured()) log.info("PayPal client configured in {} mode", mode);
    }

    PayPalClient(RestClient http, String clientId, String clientSecret, Clock clock) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return factory;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public PayPalOrderSnapshot createOrder(BigDecimal amount) throws PayPalApiException {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of("amount", Map.of(
                        "currency_code", "MXN",
                        "value", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()))));
        return call("create order", () -> snapshot(http.post()
                .uri("/v2/checkout/orders")
                .headers(h -> {
                    h.setBearerAuth(token());
                    h.set("Prefer", "return=representation");
                    h.set("PayPal-Request-Id", UUID.randomUUID().toString());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class)));
    }

    /** The order, or empty when PayPal has none with this id (e.g. the id is a capture id). */
    public Optional<PayPalOrderSnapshot> getOrder(String orderId) throws PayPalApiException {
        return call("get order " + orderId, () -> notFoundAsEmpty(() -> snapshot(http.get()
                .uri("/v2/checkout/orders/{id}", orderId)
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(JsonNode.class))));
    }

    /**
     * Captures an approved order. The request id is derived from the order id, so a retry after a
     * timeout gets PayPal's original answer instead of a second charge attempt.
     */
    public PayPalOrderSnapshot captureOrder(String orderId) throws PayPalApiException {
        return call("capture order " + orderId, () -> snapshot(http.post()
                .uri("/v2/checkout/orders/{id}/capture", orderId)
                .headers(h -> {
                    h.setBearerAuth(token());
                    h.set("Prefer", "return=representation");
                    h.set("PayPal-Request-Id", "capture-" + orderId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(JsonNode.class)));
    }

    public Optional<CaptureSnapshot> getCapture(String captureId) throws PayPalApiException {
        return call("get capture " + captureId, () -> notFoundAsEmpty(() -> {
            JsonNode capture = http.get()
                    .uri("/v2/payments/captures/{id}", captureId)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode amount = capture.path("amount");
            return new CaptureSnapshot(text(capture, "id"), text(capture, "status"),
                    text(amount, "currency_code"), decimal(amount, "value"));
        }));
    }

    /** PayPal's commission on a capture (seller_receivable_breakdown.paypal_fee); empty if not there. */
    public Optional<BigDecimal> getCaptureFee(String captureId) throws PayPalApiException {
        return call("get capture fee " + captureId, () -> notFoundAsEmpty(() -> http.get()
                .uri("/v2/payments/captures/{id}", captureId)
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(JsonNode.class))
                .map(capture -> decimal(capture.path("seller_receivable_breakdown").path("paypal_fee"), "value")));
    }

    private synchronized String token() {
        Instant now = clock.instant();
        if (accessToken == null || !now.isBefore(tokenExpiresAt.minus(TOKEN_MARGIN))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            JsonNode res = http.post()
                    .uri("/v1/oauth2/token")
                    .headers(h -> h.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            accessToken = text(res, "access_token");
            tokenExpiresAt = now.plusSeconds(res.path("expires_in").asLong(0));
        }
        return accessToken;
    }

    private interface Call<T> {
        T run();
    }

    private <T> T call(String what, Call<T> call) throws PayPalApiException {
        try {
            return call.run();
        } catch (HttpClientErrorException e) {
            // PayPal's error body names the reason (INSTRUMENT_DECLINED, ORDER_NOT_APPROVED…)
            throw new PayPalApiException("PayPal " + what + " failed: " + e.getStatusCode() + " "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException | NullPointerException e) {
            throw new PayPalApiException("PayPal " + what + " failed: " + e.getMessage(), e);
        }
    }

    private static <T> Optional<T> notFoundAsEmpty(Call<T> call) {
        try {
            return Optional.of(call.run());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    static PayPalOrderSnapshot snapshot(JsonNode order) {
        JsonNode unit = order.path("purchase_units").path(0);
        String currency = text(unit.path("amount"), "currency_code");
        BigDecimal amount = decimal(unit.path("amount"), "value");
        String captureId = null, captureStatus = null;
        JsonNode capture = unit.path("payments").path("captures").path(0);
        if (!capture.isMissingNode()) {
            captureId = text(capture, "id");
            captureStatus = text(capture, "status");
            if (capture.has("amount")) {
                currency = text(capture.path("amount"), "currency_code");
                amount = decimal(capture.path("amount"), "value");
            }
        }
        return new PayPalOrderSnapshot(text(order, "id"), text(order, "status"), currency, amount, captureId, captureStatus);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String v = text(node, field);
        return v == null ? null : new BigDecimal(v);
    }
}
