package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;

    @Value("${app.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.paypal.client-id:}")
    private String paypalClientId;

    @Value("${app.paypal.client-secret:}")
    private String paypalClientSecret;

    @Value("${app.paypal.mode:sandbox}")
    private String paypalMode;

    private PayPalHttpClient paypalClient;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
        if (paypalClientId != null && !paypalClientId.isBlank()
                && paypalClientSecret != null && !paypalClientSecret.isBlank()) {
            PayPalEnvironment environment = "live".equalsIgnoreCase(paypalMode)
                    ? new PayPalEnvironment.Live(paypalClientId, paypalClientSecret)
                    : new PayPalEnvironment.Sandbox(paypalClientId, paypalClientSecret);
            paypalClient = new PayPalHttpClient(environment);
            log.info("PayPal client initialized in {} mode", paypalMode);
        }
    }

    public boolean isPayPalConfigured() {
        return paypalClient != null;
    }

    public String getPayPalClientId() {
        return paypalClientId;
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
                if (product.getColors().isEmpty()) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                } else if (item.getSelectedSize() != null) {
                    ProductSize size = item.getSelectedSize();
                    size.setStock(size.getStock() + item.getQuantity());
                    productSizeRepository.save(size);
                }
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
                if (product.getColors().isEmpty()) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                } else if (item.getSelectedSize() != null) {
                    ProductSize size = item.getSelectedSize();
                    size.setStock(size.getStock() + item.getQuantity());
                    productSizeRepository.save(size);
                }
            }
        }
        orderRepository.save(order);
        log.info("Order {} refunded via webhook, stock restored", order.getOrderNumber());
    }

    @Transactional
    public void confirmPayPalPayment(String orderNumber, String captureId) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already confirmed", orderNumber);
            return;
        }
        order.setPaymentId(captureId);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} confirmed via PayPal capture: {}", orderNumber, captureId);
    }

    public Map<String, Object> createPayPalOrder(BigDecimal amount) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal is not configured. Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.");
        }

        OrdersCreateRequest request = new OrdersCreateRequest();
        request.prefer("return=representation");
        request.requestBody(new OrderRequest()
                .checkoutPaymentIntent("CAPTURE")
                .purchaseUnits(List.of(
                        new PurchaseUnitRequest()
                                .amountWithBreakdown(new AmountWithBreakdown()
                                        .currencyCode("USD")
                                        .value(amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()))
                )));

        try {
            HttpResponse<com.paypal.orders.Order> response = paypalClient.execute(request);
            com.paypal.orders.Order order = response.result();

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.id());
            result.put("status", order.status());
            log.info("PayPal order created: {}", order.id());
            return result;
        } catch (IOException e) {
            log.error("PayPal create order error: {}", e.getMessage());
            throw new RuntimeException("Failed to create PayPal order: " + e.getMessage());
        }
    }

    public Map<String, Object> capturePayPalOrder(String orderId) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal is not configured.");
        }

        OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
        request.prefer("return=representation");

        try {
            HttpResponse<com.paypal.orders.Order> response = paypalClient.execute(request);
            com.paypal.orders.Order order = response.result();

            String captureId = null;
            if (order.purchaseUnits() != null && !order.purchaseUnits().isEmpty()) {
                var captures = order.purchaseUnits().get(0).payments().captures();
                if (captures != null && !captures.isEmpty()) {
                    captureId = captures.get(0).id();
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.id());
            result.put("status", order.status());
            result.put("captureId", captureId);
            log.info("PayPal order captured: {} status: {}", order.id(), order.status());
            return result;
        } catch (IOException e) {
            log.error("PayPal capture order error: {}", e.getMessage());
            throw new RuntimeException("Failed to capture PayPal order: " + e.getMessage());
        }
    }
}
