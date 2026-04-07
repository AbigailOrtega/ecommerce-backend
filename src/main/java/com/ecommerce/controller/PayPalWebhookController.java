package com.ecommerce.controller;

import com.ecommerce.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PayPalWebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${app.paypal.webhook-id:}")
    private String webhookId;

    @PostMapping("/paypal")
    public ResponseEntity<String> handlePayPalWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig) {

        log.info("PayPal webhook received — transmissionId={}", transmissionId);

        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, new TypeReference<>() {});
            String eventType = payload.get("event_type") instanceof String s ? s : "";
            log.info("PayPal webhook event: {}", eventType);

            Map<?, ?> resource = payload.get("resource") instanceof Map<?, ?> r ? r : null;
            if (resource == null) {
                log.warn("PayPal webhook missing resource field");
                return ResponseEntity.ok("OK");
            }

            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED" -> handleCaptureCompleted(resource);
                case "PAYMENT.CAPTURE.DENIED",
                     "PAYMENT.CAPTURE.DECLINED" -> handleCaptureDenied(resource);
                case "PAYMENT.CAPTURE.REFUNDED" -> handleCaptureRefunded(resource);
                default -> log.info("PayPal webhook event ignored: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("OK");
    }

    private void handleCaptureCompleted(Map<?, ?> resource) {
        String captureId = resource.get("id") instanceof String s ? s : null;
        if (captureId == null) {
            log.warn("PayPal CAPTURE.COMPLETED missing capture id");
            return;
        }
        log.info("PayPal capture completed: {}", captureId);
        paymentService.handlePayPalCaptureCompleted(captureId);
    }

    private void handleCaptureDenied(Map<?, ?> resource) {
        String captureId = resource.get("id") instanceof String s ? s : null;
        if (captureId == null) {
            log.warn("PayPal CAPTURE.DENIED missing capture id");
            return;
        }
        log.info("PayPal capture denied: {}", captureId);
        paymentService.handlePayPalCaptureDenied(captureId);
    }

    private void handleCaptureRefunded(Map<?, ?> resource) {
        String captureId = resource.get("id") instanceof String s ? s : null;
        if (captureId == null) {
            log.warn("PayPal CAPTURE.REFUNDED missing capture id");
            return;
        }
        log.info("PayPal capture refunded: {}", captureId);
        paymentService.handlePayPalCaptureRefunded(captureId);
    }
}
