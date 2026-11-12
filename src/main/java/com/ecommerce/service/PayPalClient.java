package com.ecommerce.service;

import com.ecommerce.credentials.StoreKeys;
import com.ecommerce.service.PaymentService.PayPalOrderSnapshot;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
    /** The current store's PayPal account (each store charges with its own). */
    private final Supplier<StoreKeys.PayPal> account;
    private final Clock clock;

    private record Token(String value, Instant expiresAt) {}
    /** OAuth tokens per account (client id + mode). */
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    /** Thrown when PayPal cannot be reached or refuses the call (e.g. INSTRUMENT_DECLINED on capture). */
    public static class PayPalApiException extends IOException {
        PayPalApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record CaptureSnapshot(String id, String status, String currency, BigDecimal amount) {}

    @Autowired
    public PayPalClient(RestClient.Builder builder, StoreKeys storeKeys) {
        this(builder.requestFactory(timeouts()).build(), storeKeys::paypal, Clock.systemUTC());
    }

    /** With one fixed sandbox account (tests). */
    PayPalClient(RestClient http, String clientId, String clientSecret, Clock clock) {
        this(http, () -> new StoreKeys.PayPal(clientId, clientSecret, false, null), clock);
    }

    PayPalClient(RestClient http, Supplier<StoreKeys.PayPal> account, Clock clock) {
        this.http = http;
        this.account = account;
        this.clock = clock;
    }

    /** Checks an account's keys by asking PayPal for a token (the admin's "save" in Pagos). */
    public boolean credentialsWork(String clientId, String clientSecret, boolean live) {
        try {
            return fetchToken(new StoreKeys.PayPal(clientId, clientSecret, live, null)).value() != null;
        } catch (RestClientException e) {
            return false;
        }
    }

    private String base() {
        return account.get().live() ? LIVE_URL : SANDBOX_URL;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return factory;
    }

    public boolean isConfigured() {
        return account.get().ok();
    }

    public PayPalOrderSnapshot createOrder(BigDecimal amount) throws PayPalApiException {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of("amount", Map.of(
                        "currency_code", "MXN",
                        "value", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()))));
        return call("create order", () -> snapshot(http.post()
                .uri(base() + "/v2/checkout/orders")
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
                .uri(base() + "/v2/checkout/orders/{id}", orderId)
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
                .uri(base() + "/v2/checkout/orders/{id}/capture", orderId)
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
                    .uri(base() + "/v2/payments/captures/{id}", captureId)
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
                .uri(base() + "/v2/payments/captures/{id}", captureId)
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(JsonNode.class))
                .map(capture -> decimal(capture.path("seller_receivable_breakdown").path("paypal_fee"), "value")));
    }

    private String token() {
        StoreKeys.PayPal acct = account.get();
        String key = acct.clientId() + "|" + acct.live();
        Instant now = clock.instant();
        Token cached = tokens.get(key);
        if (cached == null || !now.isBefore(cached.expiresAt().minus(TOKEN_MARGIN))) {
            cached = fetchToken(acct);
            tokens.put(key, cached);
        }
        return cached.value();
    }

    private Token fetchToken(StoreKeys.PayPal acct) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        JsonNode res = http.post()
                .uri((acct.live() ? LIVE_URL : SANDBOX_URL) + "/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(acct.clientId(), acct.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        return new Token(text(res, "access_token"), clock.instant().plusSeconds(res.path("expires_in").asLong(0)));
    }

    /**
     * Makes sure the account has a webhook to {@code url} for the events the store handles, and
     * returns its id (an existing one for that URL is reused).
     */
    public String ensureWebhook(StoreKeys.PayPal acct, String url) throws PayPalApiException {
        String base = acct.live() ? LIVE_URL : SANDBOX_URL;
        return call("webhook", () -> {
            String token = fetchToken(acct).value();
            JsonNode list = http.get().uri(base + "/v1/notifications/webhooks")
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
            for (JsonNode hook : list.path("webhooks")) {
                if (url.equalsIgnoreCase(text(hook, "url"))) return text(hook, "id");
            }
            Map<String, Object> body = Map.of("url", url, "event_types", List.of(
                    Map.of("name", "PAYMENT.CAPTURE.COMPLETED"), Map.of("name", "PAYMENT.CAPTURE.DENIED"),
                    Map.of("name", "PAYMENT.CAPTURE.DECLINED"), Map.of("name", "PAYMENT.CAPTURE.REFUNDED")));
            JsonNode created = http.post().uri(base + "/v1/notifications/webhooks")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            return text(created, "id");
        });
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
