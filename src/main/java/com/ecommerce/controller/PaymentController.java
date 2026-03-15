package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payments", description = "Payment endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.stripe.public-key:}")
    private String stripePublicKey;

    @GetMapping("/stripe/config")
    @Operation(summary = "Get Stripe publishable key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStripeConfig() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("publishableKey", stripePublicKey)));
    }

    @PostMapping("/stripe/create-intent")
    @Operation(summary = "Create Stripe payment intent")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String currency = request.getOrDefault("currency", "usd").toString();
        Map<String, String> response = paymentService.createPaymentIntent(amount, currency);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/paypal/config")
    @Operation(summary = "Get PayPal client ID")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPayPalConfig() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("clientId", paymentService.getPayPalClientId())));
    }

    @PostMapping("/paypal/create-order")
    @Operation(summary = "Create PayPal order")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPayPalOrder(
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        Map<String, Object> response = paymentService.createPayPalOrder(amount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/paypal/capture-order")
    @Operation(summary = "Capture PayPal order")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayPalOrder(
            @RequestBody Map<String, Object> request) {
        String orderId = request.get("orderId").toString();
        Map<String, Object> response = paymentService.capturePayPalOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/paypal/confirm-payment")
    @Operation(summary = "Confirm order after successful PayPal capture")
    public ResponseEntity<ApiResponse<Void>> confirmPayPalPayment(
            @RequestBody Map<String, String> request) {
        paymentService.confirmPayPalPayment(request.get("orderNumber"), request.get("captureId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
