package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = StripeWebhookController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("StripeWebhookController")
class StripeWebhookControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PaymentService paymentService;

    // ── Constant test values ──────────────────────────────────────────────────

    private static final String VALID_PAYLOAD = "{\"id\":\"evt_test_001\",\"type\":\"payment_intent.succeeded\"}";
    private static final String VALID_SIG     = "t=1234567890,v1=mock_signature";

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a mock Stripe Event that returns a mock PaymentIntent via the
     * EventDataObjectDeserializer.  This avoids needing Stripe SDK internals.
     */
    private Event mockEvent(String type, String paymentIntentId) {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn(paymentIntentId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(pi));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(event.getId()).thenReturn("evt_test_001");

        return event;
    }

    private Event mockEventWithNullObject(String type) {
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        try { when(deserializer.deserializeUnsafe()).thenThrow(new RuntimeException("API version mismatch")); } catch (Exception ignored) {}

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(event.getId()).thenReturn("evt_bad_001");

        return event;
    }

    // ── POST /api/webhooks/stripe ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/webhooks/stripe")
    class HandleStripeWebhook {

        @Test
        @DisplayName("returns 400 when Stripe signature is invalid")
        void webhook_400_invalidSignature() throws Exception {
            when(paymentService.constructWebhookEvent(anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("Invalid signature", "bad_sig"));

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", "invalid-sig")
                    .content(VALID_PAYLOAD))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Invalid signature"));

            verify(paymentService, never()).handlePaymentSuccess(anyString());
            verify(paymentService, never()).handlePaymentFailure(anyString());
            verify(paymentService, never()).handleRefund(anyString());
        }

        @Test
        @DisplayName("returns 200 and calls handlePaymentSuccess on payment_intent.succeeded")
        void webhook_200_paymentIntentSucceeded() throws Exception {
            Event event = mockEvent("payment_intent.succeeded", "pi_test_001");
            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);
            doNothing().when(paymentService).handlePaymentSuccess("pi_test_001");

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content(VALID_PAYLOAD))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService).handlePaymentSuccess("pi_test_001");
            verify(paymentService, never()).handlePaymentFailure(anyString());
        }

        @Test
        @DisplayName("returns 200 and calls handlePaymentFailure on payment_intent.payment_failed")
        void webhook_200_paymentIntentFailed() throws Exception {
            Event event = mockEvent("payment_intent.payment_failed", "pi_failed_001");
            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);
            doNothing().when(paymentService).handlePaymentFailure("pi_failed_001");

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content("{\"id\":\"evt_002\",\"type\":\"payment_intent.payment_failed\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService).handlePaymentFailure("pi_failed_001");
            verify(paymentService, never()).handlePaymentSuccess(anyString());
        }

        @Test
        @DisplayName("returns 200 and calls handleRefund on charge.refunded")
        void webhook_200_chargeRefunded() throws Exception {
            com.stripe.model.Charge charge = mock(com.stripe.model.Charge.class);
            when(charge.getPaymentIntent()).thenReturn("pi_refund_001");

            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(deserializer.getObject()).thenReturn(Optional.of(charge));

            Event event = mock(Event.class);
            when(event.getType()).thenReturn("charge.refunded");
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);

            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);
            doNothing().when(paymentService).handleRefund("pi_refund_001");

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content("{\"id\":\"evt_003\",\"type\":\"charge.refunded\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService).handleRefund("pi_refund_001");
        }

        @Test
        @DisplayName("returns 200 and skips handleRefund when charge has no paymentIntent")
        void webhook_200_chargeRefunded_noPaymentIntent() throws Exception {
            com.stripe.model.Charge charge = mock(com.stripe.model.Charge.class);
            when(charge.getPaymentIntent()).thenReturn(null);

            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(deserializer.getObject()).thenReturn(Optional.of(charge));

            Event event = mock(Event.class);
            when(event.getType()).thenReturn("charge.refunded");
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);

            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content("{\"id\":\"evt_003\",\"type\":\"charge.refunded\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService, never()).handleRefund(anyString());
        }

        @Test
        @DisplayName("returns 200 and ignores unknown event types")
        void webhook_200_unknownEventType() throws Exception {
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe()).thenReturn(mock(com.stripe.model.StripeObject.class));

            Event event = mock(Event.class);
            when(event.getType()).thenReturn("customer.created");
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);

            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content("{\"id\":\"evt_unknown\",\"type\":\"customer.created\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService, never()).handlePaymentSuccess(anyString());
            verify(paymentService, never()).handlePaymentFailure(anyString());
            verify(paymentService, never()).handleRefund(anyString());
        }

        @Test
        @DisplayName("returns 200 when typed deserialization fails and unsafe deserialization also fails")
        void webhook_200_deserializationFails() throws Exception {
            Event event = mockEventWithNullObject("payment_intent.succeeded");
            when(paymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Stripe-Signature", VALID_SIG)
                    .content(VALID_PAYLOAD))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            // When deserialization fails entirely, the handler returns early with OK
            verify(paymentService, never()).handlePaymentSuccess(anyString());
        }

        @Test
        @DisplayName("returns 400 when Stripe-Signature header is missing")
        void webhook_400_missingSignatureHeader() throws Exception {
            // Spring will invoke the method with a null sigHeader only if the header
            // is declared @RequestHeader (required=true by default), causing a
            // MissingRequestHeaderException -> 400 before even reaching the service.
            mockMvc.perform(post("/api/webhooks/stripe")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content(VALID_PAYLOAD))
                    .andExpect(status().isBadRequest());

            verify(paymentService, never()).constructWebhookEvent(anyString(), anyString());
        }
    }
}
