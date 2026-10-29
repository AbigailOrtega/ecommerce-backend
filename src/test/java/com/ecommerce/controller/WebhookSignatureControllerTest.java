package com.ecommerce.controller;

import com.ecommerce.entity.Order;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PayPalWebhookVerifier;
import com.ecommerce.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookSignatureControllerTest {

    private static final String PAYPAL_BODY =
            "{\"event_type\":\"PAYMENT.CAPTURE.REFUNDED\",\"resource\":{\"id\":\"CAP-1\"}}";
    private static final String SKYDROPX_BODY =
            "{\"data\":{\"id\":\"SHP-1\",\"status\":\"delivered\"}}";
    private static final String SKYDROPX_SECRET = "skydropx-test-secret";

    private PaymentService paymentService;
    private PayPalWebhookVerifier verifier;
    private OrderRepository orderRepository;
    private SkydropxWebhookController skydropxController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        verifier = mock(PayPalWebhookVerifier.class);
        orderRepository = mock(OrderRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        skydropxController = new SkydropxWebhookController(orderRepository, mapper);
        ReflectionTestUtils.setField(skydropxController, "webhookSecret", SKYDROPX_SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PayPalWebhookController(paymentService, verifier, mapper), skydropxController).build();
    }

    // ── PayPal ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PayPal: forged webhook (signature not verified) → 401, no order touched")
    void paypal_forged() throws Exception {
        when(verifier.verify(anyString(), any(), any(), any(), any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(PAYPAL_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("PayPal: verified webhook → 200 and event handled")
    void paypal_verified() throws Exception {
        when(verifier.verify(eq(PAYPAL_BODY), eq("tx-1"), eq("t"), eq("https://api.paypal.com/c"), eq("SHA256withRSA"), eq("sig")))
                .thenReturn(true);

        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(PAYPAL_BODY)
                        .header("PAYPAL-TRANSMISSION-ID", "tx-1")
                        .header("PAYPAL-TRANSMISSION-TIME", "t")
                        .header("PAYPAL-CERT-URL", "https://api.paypal.com/c")
                        .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                        .header("PAYPAL-TRANSMISSION-SIG", "sig"))
                .andExpect(status().isOk());

        verify(paymentService).handlePayPalCaptureRefunded("CAP-1");
    }

    // ── Skydropx ─────────────────────────────────────────────────────────────

    private static String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Skydropx: valid HMAC → 200 and order updated")
    void skydropx_valid() throws Exception {
        Order order = Order.builder().orderNumber("ORD-1").build();
        when(orderRepository.findBySkydropxShipmentId("SHP-1")).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/webhooks/skydropx").contentType(MediaType.APPLICATION_JSON).content(SKYDROPX_BODY)
                        .header("Authorization", "HMAC " + hmac(SKYDROPX_BODY, SKYDROPX_SECRET)))
                .andExpect(status().isOk());

        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Skydropx: wrong HMAC, missing header or non-hex signature → 401")
    void skydropx_invalid() throws Exception {
        mockMvc.perform(post("/api/webhooks/skydropx").contentType(MediaType.APPLICATION_JSON).content(SKYDROPX_BODY)
                        .header("Authorization", "HMAC " + hmac(SKYDROPX_BODY, "wrong-secret")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/skydropx").contentType(MediaType.APPLICATION_JSON).content(SKYDROPX_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/skydropx").contentType(MediaType.APPLICATION_JSON).content(SKYDROPX_BODY)
                        .header("Authorization", "HMAC zz-not-hex"))
                .andExpect(status().isUnauthorized());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Skydropx: secret not configured → every request rejected with 401")
    void skydropx_notConfigured() throws Exception {
        ReflectionTestUtils.setField(skydropxController, "webhookSecret", "");

        mockMvc.perform(post("/api/webhooks/skydropx").contentType(MediaType.APPLICATION_JSON).content(SKYDROPX_BODY)
                        .header("Authorization", "HMAC " + hmac(SKYDROPX_BODY, SKYDROPX_SECRET)))
                .andExpect(status().isUnauthorized());

        verify(orderRepository, never()).save(any());
    }
}
