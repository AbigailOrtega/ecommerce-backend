package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = PaymentController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@TestPropertySource(properties = "app.stripe.public-key=pk_test_mock_key")
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PaymentService paymentService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "customer@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    // ── GET /api/payments/stripe/config ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/payments/stripe/config")
    class GetStripeConfig {

        @Test
        @DisplayName("returns 200 with the publishable key")
        void getStripeConfig_200() throws Exception {
            mockMvc.perform(get("/api/payments/stripe/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.publishableKey").value("pk_test_mock_key"));
        }

        @Test
        @DisplayName("returns 200 without authentication (public endpoint)")
        void getStripeConfig_200_noAuth() throws Exception {
            // Stripe config is listed as permitAll in SecurityConfig; security is
            // excluded here, so calling without auth must still work.
            mockMvc.perform(get("/api/payments/stripe/config"))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /api/payments/stripe/create-intent ───────────────────────────────

    @Nested
    @DisplayName("POST /api/payments/stripe/create-intent")
    class CreatePaymentIntent {

        @Test
        @DisplayName("returns 200 with clientSecret and paymentIntentId")
        void createIntent_200() throws Exception {
            Map<String, String> serviceResponse = Map.of(
                    "clientSecret", "pi_mock_secret_test",
                    "paymentIntentId", "pi_mock_123");
            when(paymentService.createPaymentIntent(new BigDecimal("49.99"), "usd"))
                    .thenReturn(serviceResponse);

            String body = "{\"amount\":49.99,\"currency\":\"usd\"}";

            mockMvc.perform(post("/api/payments/stripe/create-intent")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.clientSecret").value("pi_mock_secret_test"))
                    .andExpect(jsonPath("$.data.paymentIntentId").value("pi_mock_123"));
        }

        @Test
        @DisplayName("uses 'usd' as default currency when currency is not provided")
        void createIntent_200_defaultCurrency() throws Exception {
            Map<String, String> serviceResponse = Map.of(
                    "clientSecret", "pi_mock_secret",
                    "paymentIntentId", "pi_mock_456");
            when(paymentService.createPaymentIntent(any(), anyString())).thenReturn(serviceResponse);

            String body = "{\"amount\":29.99}";

            mockMvc.perform(post("/api/payments/stripe/create-intent")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            // Verify the controller passed the default "usd" value
            verify(paymentService).createPaymentIntent(new BigDecimal("29.99"), "usd");
        }

        @Test
        @DisplayName("returns 400 when Stripe is not configured")
        void createIntent_400_stripeNotConfigured() throws Exception {
            when(paymentService.createPaymentIntent(any(), anyString()))
                    .thenThrow(new BadRequestException(
                            "Stripe is not configured. Set STRIPE_SECRET_KEY to enable card payments."));

            String body = "{\"amount\":49.99,\"currency\":\"usd\"}";

            mockMvc.perform(post("/api/payments/stripe/create-intent")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 500 when Stripe API call fails")
        void createIntent_500_stripeError() throws Exception {
            when(paymentService.createPaymentIntent(any(), anyString()))
                    .thenThrow(new RuntimeException("Payment processing failed: Your card was declined."));

            String body = "{\"amount\":49.99,\"currency\":\"usd\"}";

            mockMvc.perform(post("/api/payments/stripe/create-intent")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/payments/paypal/config ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/payments/paypal/config")
    class GetPayPalConfig {

        @Test
        @DisplayName("returns 200 with the PayPal client ID")
        void getPayPalConfig_200() throws Exception {
            when(paymentService.getPayPalClientId()).thenReturn("paypal-client-id-mock");

            mockMvc.perform(get("/api/payments/paypal/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.clientId").value("paypal-client-id-mock"));
        }

        @Test
        @DisplayName("returns 200 with empty clientId when PayPal is not configured")
        void getPayPalConfig_200_notConfigured() throws Exception {
            when(paymentService.getPayPalClientId()).thenReturn("");

            mockMvc.perform(get("/api/payments/paypal/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.clientId").value(""));
        }
    }

    // ── POST /api/payments/paypal/create-order ────────────────────────────────

    @Nested
    @DisplayName("POST /api/payments/paypal/create-order")
    class CreatePayPalOrder {

        @Test
        @DisplayName("returns 200 with orderId and status on success")
        void createPayPalOrder_200() throws Exception {
            Map<String, Object> serviceResponse = Map.of(
                    "orderId", "PAYPAL-ORDER-001",
                    "status", "CREATED");
            when(paymentService.createPayPalOrder(new BigDecimal("99.99"))).thenReturn(serviceResponse);

            String body = "{\"amount\":99.99}";

            mockMvc.perform(post("/api/payments/paypal/create-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("PAYPAL-ORDER-001"))
                    .andExpect(jsonPath("$.data.status").value("CREATED"));
        }

        @Test
        @DisplayName("returns 400 when PayPal is not configured")
        void createPayPalOrder_400_notConfigured() throws Exception {
            when(paymentService.createPayPalOrder(any()))
                    .thenThrow(new BadRequestException(
                            "PayPal is not configured. Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET."));

            String body = "{\"amount\":99.99}";

            mockMvc.perform(post("/api/payments/paypal/create-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 500 when PayPal API call fails")
        void createPayPalOrder_500_apiError() throws Exception {
            when(paymentService.createPayPalOrder(any()))
                    .thenThrow(new RuntimeException("Failed to create PayPal order: connect timed out"));

            String body = "{\"amount\":99.99}";

            mockMvc.perform(post("/api/payments/paypal/create-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── POST /api/payments/paypal/capture-order ───────────────────────────────

    @Nested
    @DisplayName("POST /api/payments/paypal/capture-order")
    class CapturePayPalOrder {

        @Test
        @DisplayName("returns 200 with capture details on success")
        void capturePayPalOrder_200() throws Exception {
            Map<String, Object> serviceResponse = Map.of(
                    "orderId", "PAYPAL-ORDER-001",
                    "status", "COMPLETED",
                    "captureId", "CAP-001");
            when(paymentService.capturePayPalOrder("PAYPAL-ORDER-001")).thenReturn(serviceResponse);

            String body = "{\"orderId\":\"PAYPAL-ORDER-001\"}";

            mockMvc.perform(post("/api/payments/paypal/capture-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.captureId").value("CAP-001"));
        }

        @Test
        @DisplayName("returns 400 when PayPal is not configured")
        void capturePayPalOrder_400_notConfigured() throws Exception {
            when(paymentService.capturePayPalOrder(anyString()))
                    .thenThrow(new BadRequestException("PayPal is not configured."));

            String body = "{\"orderId\":\"PAYPAL-ORDER-001\"}";

            mockMvc.perform(post("/api/payments/paypal/capture-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 500 when PayPal capture fails")
        void capturePayPalOrder_500_captureError() throws Exception {
            when(paymentService.capturePayPalOrder(anyString()))
                    .thenThrow(new RuntimeException("Failed to capture PayPal order: INSTRUMENT_DECLINED"));

            String body = "{\"orderId\":\"PAYPAL-ORDER-001\"}";

            mockMvc.perform(post("/api/payments/paypal/capture-order")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── POST /api/payments/paypal/confirm-payment ─────────────────────────────

    @Nested
    @DisplayName("POST /api/payments/paypal/confirm-payment")
    class ConfirmPayPalPayment {

        @Test
        @DisplayName("returns 200 on successful payment confirmation")
        void confirmPayment_200() throws Exception {
            doNothing().when(paymentService).confirmPayPalPayment("ORD-2025-001", "CAP-001");

            String body = "{\"orderNumber\":\"ORD-2025-001\",\"captureId\":\"CAP-001\"}";

            mockMvc.perform(post("/api/payments/paypal/confirm-payment")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(paymentService).confirmPayPalPayment("ORD-2025-001", "CAP-001");
        }

        @Test
        @DisplayName("returns 500 when order number is not found")
        void confirmPayment_500_orderNotFound() throws Exception {
            doThrow(new RuntimeException("Order not found: ORD-UNKNOWN"))
                    .when(paymentService).confirmPayPalPayment(eq("ORD-UNKNOWN"), anyString());

            String body = "{\"orderNumber\":\"ORD-UNKNOWN\",\"captureId\":\"CAP-001\"}";

            mockMvc.perform(post("/api/payments/paypal/confirm-payment")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
