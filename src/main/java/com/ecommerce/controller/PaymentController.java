package com.ecommerce.controller;

import com.ecommerce.dto.request.PayPalCaptureForOrderRequest;
import com.ecommerce.dto.request.PayPalCaptureOrderRequest;
import com.ecommerce.dto.request.PayPalConfirmPaymentRequest;
import com.ecommerce.dto.request.PaymentAmountRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payments", description = "Payment endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/stripe/config")
    @Operation(summary = "Get Stripe publishable key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStripeConfig() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("publishableKey", paymentService.getStripePublicKey())));
    }

    @PostMapping("/stripe/create-intent")
    @Operation(summary = "Create Stripe payment intent")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
            @Valid @RequestBody PaymentAmountRequest request) {
        Map<String, String> response = paymentService.createPaymentIntent(request.amount(), request.currencyOrDefault());
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
            @Valid @RequestBody PaymentAmountRequest request) {
        Map<String, Object> response = paymentService.createPayPalOrder(request.amount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/paypal/capture-for-order")
    @Operation(summary = "Charge an order paid with PayPal, after the order was created")
    public ResponseEntity<ApiResponse<Void>> capturePayPalForOrder(@Valid @RequestBody PayPalCaptureForOrderRequest request) {
        paymentService.capturePayPalForOrder(request.orderNumber());
        return ResponseEntity.ok(ApiResponse.success("Pago completado", null));
    }

    /** Legacy: captured before the order existed (pages loaded before capture-for-order). */
    @PostMapping("/paypal/capture-order")
    @Operation(summary = "Capture PayPal order (legacy)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayPalOrder(
            @Valid @RequestBody PayPalCaptureOrderRequest request) {
        Map<String, Object> response = paymentService.capturePayPalOrder(request.orderId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/paypal/confirm-payment")
    @Operation(summary = "Confirm order after successful PayPal capture")
    public ResponseEntity<ApiResponse<Void>> confirmPayPalPayment(
            @Valid @RequestBody PayPalConfirmPaymentRequest request) {
        paymentService.confirmPayPalPayment(request.orderNumber(), request.captureId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
