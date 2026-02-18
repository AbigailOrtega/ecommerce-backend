package com.ecommerce.controller;

import com.ecommerce.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = paymentService.constructWebhookEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        String eventType = event.getType();
        log.info("Received Stripe event: {}", eventType);

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);

        // If typed deserialization fails (API version mismatch), deserialize without version check
        if (stripeObject == null) {
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.error("Failed to deserialize Stripe event {}: {}", event.getId(), e.getMessage());
                return ResponseEntity.ok("OK");
            }
        }

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                PaymentIntent pi = (PaymentIntent) stripeObject;
                log.info("payment_intent.succeeded for pi_id={}", pi.getId());
                paymentService.handlePaymentSuccess(pi.getId());
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent pi = (PaymentIntent) stripeObject;
                log.info("payment_intent.payment_failed for pi_id={}", pi.getId());
                paymentService.handlePaymentFailure(pi.getId());
            }
            case "charge.refunded" -> {
                com.stripe.model.Charge charge = (com.stripe.model.Charge) stripeObject;
                if (charge.getPaymentIntent() != null) {
                    log.info("charge.refunded for pi_id={}", charge.getPaymentIntent());
                    paymentService.handleRefund(charge.getPaymentIntent());
                }
            }
            default -> log.info("Unhandled event type: {}", eventType);
        }

        return ResponseEntity.ok("OK");
    }
}
