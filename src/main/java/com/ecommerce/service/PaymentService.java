package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import com.paypal.payments.Capture;
import com.paypal.payments.CapturesGetRequest;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentUpdateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * Called by OrderService before saving a new order. Makes sure the payment referenced by the
     * client belongs to no other order and is for exactly the total computed by the server.
     * Payment methods other than stripe/paypal are left untouched.
     */
    public void verifyPaymentForOrder(String paymentMethod, String paymentId, BigDecimal orderTotal) {
        boolean stripe = "stripe".equalsIgnoreCase(paymentMethod);
        boolean paypal = "paypal".equalsIgnoreCase(paymentMethod);
        if (!stripe && !paypal) return;

        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("paymentId es requerido para el método de pago " + paymentMethod);
        }
        if (orderRepository.findByPaymentId(paymentId).isPresent()) {
            throw new BadRequestException("Este pago ya está asociado a otro pedido");
        }

        if (stripe) {
            verifyStripeIntentForOrder(paymentId, orderTotal);
        } else {
            verifyPayPalCapture(paymentId, orderTotal);
        }
    }

    private void verifyStripeIntentForOrder(String paymentIntentId, BigDecimal orderTotal) {
        if (!isStripeConfigured()) {
            throw new BadRequestException("Stripe is not configured.");
        }
        long expectedCents = toCents(orderTotal);
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if (!"mxn".equalsIgnoreCase(intent.getCurrency())) {
                throw new BadRequestException("Moneda de pago inválida");
            }
            if (intent.getAmount() == expectedCents) return;

            // The intent is created before the order, from the client's total. If it has not been
            // confirmed yet, bring it in line with the server's total; otherwise reject the order.
            if (UPDATABLE_INTENT_STATUSES.contains(intent.getStatus())) {
                log.warn("PaymentIntent {} amount {} != order total {}, updating intent",
                        paymentIntentId, intent.getAmount(), expectedCents);
                intent.update(PaymentIntentUpdateParams.builder().setAmount(expectedCents).build());
            } else {
                throw new BadRequestException("El monto del pago no coincide con el total del pedido");
            }
        } catch (StripeException e) {
            log.error("Stripe error verifying intent {}: {}", paymentIntentId, e.getMessage());
            throw new BadRequestException("No se pudo verificar el pago con Stripe");
        }
    }

    private void verifyPayPalCapture(String captureId, BigDecimal orderTotal) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal is not configured.");
        }
        Capture capture;
        try {
            capture = paypalClient.execute(new CapturesGetRequest(captureId)).result();
        } catch (IOException e) {
            log.error("PayPal error fetching capture {}: {}", captureId, e.getMessage());
            throw new BadRequestException("No se pudo verificar el pago con PayPal");
        }
        if (capture == null || !"COMPLETED".equalsIgnoreCase(capture.status())) {
            throw new BadRequestException("El pago de PayPal no está completado");
        }
        if (capture.amount() == null
                || !"MXN".equalsIgnoreCase(capture.amount().currencyCode())
                || new BigDecimal(capture.amount().value()).compareTo(orderTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            log.error("PayPal capture {} amount {} does not match order total {}",
                    captureId, capture.amount() != null ? capture.amount().value() : null, orderTotal);
            throw new BadRequestException("El monto del pago no coincide con el total del pedido");
        }
    }

    private static final Set<String> UPDATABLE_INTENT_STATUSES =
            Set.of("requires_payment_method", "requires_confirmation", "requires_action");

    private static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    @Transactional
    public void handlePaymentSuccess(String paymentIntentId, long amountReceived, String currency) {
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
        if (!"mxn".equalsIgnoreCase(currency) || amountReceived != toCents(order.getTotalAmount())) {
            log.error("Order {} NOT confirmed: Stripe received {} {} but order total is {} — review manually",
                    order.getOrderNumber(), amountReceived, currency, order.getTotalAmount());
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
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already confirmed", orderNumber);
            return;
        }
        // The capture was verified and attached to the order when it was created; only that
        // capture can confirm it, and we re-check with PayPal that it is still completed.
        if (!"paypal".equalsIgnoreCase(order.getPaymentMethod())
                || captureId == null || !captureId.equals(order.getPaymentId())) {
            throw new BadRequestException("El pago no corresponde a este pedido");
        }
        verifyPayPalCapture(captureId, order.getTotalAmount());
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} confirmed via PayPal capture: {}", orderNumber, captureId);
    }

    @Transactional
    public void handlePayPalCaptureCompleted(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                log.info("Order {} already confirmed, skipping", order.getOrderNumber());
                return;
            }
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("Order {} confirmed via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.COMPLETED — no order found for captureId={}", captureId));
    }

    @Transactional
    public void handlePayPalCaptureDenied(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) return;
            order.setStatus(OrderStatus.CANCELLED);
            restoreStock(order);
            orderRepository.save(order);
            log.info("Order {} cancelled via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.DENIED — no order found for captureId={}", captureId));
    }

    @Transactional
    public void handlePayPalCaptureRefunded(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.REFUNDED) return;
            order.setStatus(OrderStatus.REFUNDED);
            restoreStock(order);
            orderRepository.save(order);
            log.info("Order {} refunded via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.REFUNDED — no order found for captureId={}", captureId));
    }

    private void restoreStock(Order order) {
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
                                        .currencyCode("MXN")
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
