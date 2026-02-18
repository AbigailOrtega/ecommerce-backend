package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Value("${app.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    public boolean isStripeConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public Map<String, String> createPaymentIntent(BigDecimal amount, String currency) {
        if (!isStripeConfigured()) {
            throw new BadRequestException("Stripe is not configured. Set STRIPE_SECRET_KEY to enable card payments.");
        }

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            Map<String, String> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());
            return response;
        } catch (StripeException e) {
            log.error("Stripe error: {}", e.getMessage());
            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }

    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    @Transactional
    public void handlePaymentSuccess(String paymentIntentId) {
        Optional<Order> orderOpt = orderRepository.findByPaymentId(paymentIntentId);
        if (orderOpt.isEmpty()) {
            log.warn("No order found for paymentIntentId: {}", paymentIntentId);
            return;
        }
        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already confirmed", order.getOrderNumber());
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} confirmed via webhook", order.getOrderNumber());
    }

    @Transactional
    public void handlePaymentFailure(String paymentIntentId) {
        Optional<Order> orderOpt = orderRepository.findByPaymentId(paymentIntentId);
        if (orderOpt.isEmpty()) {
            log.warn("No order found for paymentIntentId: {}", paymentIntentId);
            return;
        }
        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} already cancelled", order.getOrderNumber());
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }
        orderRepository.save(order);
        log.info("Order {} cancelled due to payment failure, stock restored", order.getOrderNumber());
    }

    @Transactional
    public void handleRefund(String paymentIntentId) {
        Optional<Order> orderOpt = orderRepository.findByPaymentId(paymentIntentId);
        if (orderOpt.isEmpty()) {
            log.warn("No order found for paymentIntentId: {}", paymentIntentId);
            return;
        }
        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info("Order {} already refunded", order.getOrderNumber());
            return;
        }
        order.setStatus(OrderStatus.REFUNDED);
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }
        orderRepository.save(order);
        log.info("Order {} refunded via webhook, stock restored", order.getOrderNumber());
    }

    public Map<String, Object> createPayPalOrder(BigDecimal amount) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", "PAYPAL-" + System.currentTimeMillis());
        response.put("amount", amount);
        response.put("status", "CREATED");
        log.info("PayPal order created for amount: {}", amount);
        return response;
    }
}
