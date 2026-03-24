package com.ecommerce.controller;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class SkydropxWebhookController {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.skydropx.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/skydropx")
    public ResponseEntity<String> handleSkydropxWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("Skydropx webhook received");

        // Verify HMAC-SHA512 signature if secret is configured
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (authHeader == null || !authHeader.startsWith("HMAC ")) {
                log.warn("Skydropx webhook rejected — missing or invalid Authorization header");
                return ResponseEntity.status(401).body("Unauthorized");
            }
            String receivedSignature = authHeader.substring("HMAC ".length()).trim();
            if (!verifyHmac(rawBody, receivedSignature)) {
                log.warn("Skydropx webhook rejected — HMAC signature mismatch");
                return ResponseEntity.status(401).body("Unauthorized");
            }
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, new TypeReference<>() {});
            log.info("Skydropx webhook payload: {}", payload);

            Map<?, ?> data = payload.get("data") instanceof Map<?, ?> d ? d : null;
            if (data == null) {
                log.warn("Skydropx webhook missing 'data' field");
                return ResponseEntity.ok("OK");
            }

            String shipmentId  = data.get("id")              != null ? String.valueOf(data.get("id"))              : null;
            String status      = data.get("status")          != null ? String.valueOf(data.get("status"))          : "";
            String tracking    = data.get("tracking_number") != null ? String.valueOf(data.get("tracking_number")) : null;
            String carrier     = data.get("carrier")         != null ? String.valueOf(data.get("carrier"))         : null;

            if (shipmentId == null || shipmentId.isBlank()) {
                log.warn("Skydropx webhook missing shipment ID");
                return ResponseEntity.ok("OK");
            }

            log.info("Skydropx webhook — shipmentId={} status={} tracking={}", shipmentId, status, tracking);

            Optional<Order> orderOpt = orderRepository.findBySkydropxShipmentId(shipmentId);
            if (orderOpt.isEmpty()) {
                log.warn("Skydropx webhook — no order found for shipmentId={}", shipmentId);
                return ResponseEntity.ok("OK");
            }

            Order order = orderOpt.get();
            order.setShipmentStatus(status);
            if (tracking != null && !tracking.isBlank()) order.setTrackingNumber(tracking);
            if (carrier  != null && !carrier.isBlank())  order.setCarrierName(carrier);

            OrderStatus newOrderStatus = mapToOrderStatus(status);
            if (newOrderStatus != null) {
                order.setStatus(newOrderStatus);
                log.info("Order {} status updated to {} via Skydropx webhook (shipmentStatus={})",
                        order.getOrderNumber(), newOrderStatus, status);
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("Error processing Skydropx webhook: {}", e.getMessage(), e);
        }

        // Always return 200 so Skydropx does not retry
        return ResponseEntity.ok("OK");
    }

    private boolean verifyHmac(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA512"));
            byte[] computed = mac.doFinal(payload.getBytes());
            String computedHex = HexFormat.of().formatHex(computed);
            return computedHex.equalsIgnoreCase(receivedSignature);
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    private OrderStatus mapToOrderStatus(String skydropxStatus) {
        if (skydropxStatus == null) return null;
        return switch (skydropxStatus.toLowerCase()) {
            case "picked_up", "in_transit", "last_mile" -> OrderStatus.SHIPPED;
            case "delivered"                             -> OrderStatus.DELIVERED;
            case "delivery_attempt"                      -> OrderStatus.DELIVERY_ATTEMPT;
            case "exception"                             -> OrderStatus.DELIVERY_EXCEPTION;
            default                                      -> null;
        };
    }
}
