package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.credentials.StoreKeys;
import com.stripe.net.RequestOptions;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentUpdateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final CartService cartService;
    private final PayPalClient paypalClient;
    private final ApplicationEventPublisher events;

    /** The current store's own Stripe/PayPal accounts (a field, so subclasses keep their constructor). */
    @Autowired
    private StoreKeys storeKeys;

    private StoreKeys.Stripe stripeKeys() {
        return storeKeys != null ? storeKeys.stripe() : new StoreKeys.Stripe(null, null, null);
    }

    /** Every Stripe call goes with the current store's secret key, never a global one. */
    protected RequestOptions stripeOptions() {
        return RequestOptions.builder().setApiKey(stripeKeys().secretKey()).build();
    }

    public boolean isPayPalConfigured() {
        return paypalClient != null && paypalClient.isConfigured();
    }

    /** The store's PayPal client id for the checkout's buttons; empty when it has no PayPal. */
    public String getPayPalClientId() {
        String id = storeKeys != null && storeKeys.paypal().ok() ? storeKeys.paypal().clientId() : null;
        return id != null ? id : "";
    }

    public boolean isStripeConfigured() {
        String key = stripeKeys().secretKey();
        return key != null && !key.isBlank();
    }

    /** The store's publishable key for the checkout; empty when it has no Stripe (the card option is hidden). */
    public String getStripePublicKey() {
        String key = isStripeConfigured() ? stripeKeys().publicKey() : null;
        return key != null ? key : "";
    }

    public Map<String, String> createPaymentIntent(BigDecimal amount, String currency) {
        if (!isStripeConfigured()) {
            throw new BadRequestException("Esta tienda aún no acepta pagos con tarjeta.");
        }

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toCents(amount))
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params, stripeOptions());

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
        String secret = stripeKeys().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new SignatureVerificationException("This store has no Stripe webhook secret", sigHeader);
        }
        return Webhook.constructEvent(payload, sigHeader, secret);
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
            Optional<PayPalOrderSnapshot> paypalOrder = findPayPalOrder(paymentId);
            if (paypalOrder.isPresent()) {
                verifyApprovedPayPalOrder(paypalOrder.get(), orderTotal);
            } else {
                // Legacy flow (checkout pages loaded before the change): captured before the order existed.
                verifyPayPalCapture(paymentId, orderTotal);
            }
        }
    }

    /** The buyer approved the payment in PayPal; it is captured only after the order exists. */
    private void verifyApprovedPayPalOrder(PayPalOrderSnapshot paypalOrder, BigDecimal orderTotal) {
        if (!"APPROVED".equalsIgnoreCase(paypalOrder.status())) {
            throw new BadRequestException("El pago de PayPal no fue aprobado");
        }
        if (!"MXN".equalsIgnoreCase(paypalOrder.currency())
                || paypalOrder.amount() == null
                || paypalOrder.amount().compareTo(orderTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            log.error("PayPal order {} amount {} {} does not match order total {}",
                    paypalOrder.id(), paypalOrder.amount(), paypalOrder.currency(), orderTotal);
            throw new BadRequestException("El monto del pago no coincide con el total del pedido");
        }
    }

    protected void verifyStripeIntentForOrder(String paymentIntentId, BigDecimal orderTotal) {
        if (!isStripeConfigured()) {
            throw new BadRequestException("Stripe no está configurado.");
        }
        long expectedCents = toCents(orderTotal);
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, stripeOptions());
            if (!"mxn".equalsIgnoreCase(intent.getCurrency())) {
                throw new BadRequestException("Moneda de pago inválida");
            }
            if (intent.getAmount() == expectedCents) return;

            // The intent is created before the order, from the client's total. If it has not been
            // confirmed yet, bring it in line with the server's total; otherwise reject the order.
            if (UPDATABLE_INTENT_STATUSES.contains(intent.getStatus())) {
                log.warn("PaymentIntent {} amount {} != order total {}, updating intent",
                        paymentIntentId, intent.getAmount(), expectedCents);
                intent.update(PaymentIntentUpdateParams.builder().setAmount(expectedCents).build(), stripeOptions());
            } else {
                throw new BadRequestException("El monto del pago no coincide con el total del pedido");
            }
        } catch (StripeException e) {
            log.error("Stripe error verifying intent {}: {}", paymentIntentId, e.getMessage());
            throw new BadRequestException("No se pudo verificar el pago con Stripe");
        }
    }

    protected void verifyPayPalCapture(String captureId, BigDecimal orderTotal) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal no está configurado.");
        }
        PayPalClient.CaptureSnapshot capture;
        try {
            capture = paypalClient.getCapture(captureId).orElse(null);
        } catch (IOException e) {
            log.error("PayPal error fetching capture {}: {}", captureId, e.getMessage());
            throw new BadRequestException("No se pudo verificar el pago con PayPal");
        }
        if (capture == null || !"COMPLETED".equalsIgnoreCase(capture.status())) {
            throw new BadRequestException("El pago de PayPal no está completado");
        }
        if (capture.amount() == null
                || !"MXN".equalsIgnoreCase(capture.currency())
                || capture.amount().compareTo(orderTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            log.error("PayPal capture {} amount {} does not match order total {}",
                    captureId, capture.amount(), orderTotal);
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
        // Stripe may resend an event or deliver it late: only a PENDING order is confirmed
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.error("Order {} was already cancelled (stock released) when Stripe reported payment {} — "
                    + "refund it or restore the order by hand", order.getOrderNumber(), paymentIntentId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {}; payment event ignored", order.getOrderNumber(), order.getStatus());
            return;
        }
        if (!"mxn".equalsIgnoreCase(currency) || amountReceived != toCents(order.getTotalAmount())) {
            log.error("Order {} NOT confirmed: Stripe received {} {} but order total is {} — review manually",
                    order.getOrderNumber(), amountReceived, currency, order.getTotalAmount());
            return;
        }
        markPaid(order);
        log.info("Order {} confirmed via webhook", order.getOrderNumber());
    }

    /** The intent was cancelled for good (payment_intent.canceled): release the unpaid order's stock. */
    @Transactional
    public void handlePaymentFailure(String paymentIntentId) {
        Optional<Order> orderOpt = orderRepository.findByPaymentId(paymentIntentId);
        if (orderOpt.isEmpty()) {
            log.warn("No order found for paymentIntentId: {}", paymentIntentId);
            return;
        }
        Order order = orderOpt.get();
        // only an unpaid order is cancelled: a late or repeated event must not cancel a paid one
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is {}; cancellation event ignored", order.getOrderNumber(), order.getStatus());
            return;
        }
        stockService.restore(order, order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        cartService.restoreFromOrder(order);
        log.info("Order {} cancelled due to payment failure, stock and cart restored", order.getOrderNumber());
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
        // a CANCELLED order already released its stock; restore() knows and skips it
        stockService.restore(order, order.getStatus());
        order.setStatus(OrderStatus.REFUNDED);
        order.markRefundedAt(LocalDateTime.now());
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
        markPaid(order);
        log.info("Order {} confirmed via PayPal capture: {}", orderNumber, captureId);
    }

    /**
     * Charges an order paid with PayPal. The order was created (stock reserved) with the approved
     * PayPal order id; if PayPal declines the capture the order is cancelled and its stock restored,
     * so the buyer is never charged for an order that does not exist.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void capturePayPalForOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        if (!"paypal".equalsIgnoreCase(order.getPaymentMethod()) || order.getPaymentId() == null) {
            throw new BadRequestException("El pedido no se paga con PayPal");
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) return;
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("El pedido ya no está pendiente de pago");
        }

        String paypalOrderId = order.getPaymentId();
        PayPalOrderSnapshot captured;
        try {
            captured = capturePayPal(paypalOrderId);
        } catch (IOException e) {
            // a previous attempt may already have captured it
            captured = findPayPalOrder(paypalOrderId)
                    .filter(o -> "COMPLETED".equalsIgnoreCase(o.status()))
                    .orElse(null);
            if (captured == null) {
                log.warn("PayPal capture failed for order {} (PayPal order {}): {}", orderNumber, paypalOrderId, e.getMessage());
                cancelUnpaidOrder(order);
                cartService.restoreFromOrder(order);
                throw new BadRequestException("PayPal no pudo completar el pago. El pedido fue cancelado; intenta de nuevo.");
            }
        }

        if (!"COMPLETED".equalsIgnoreCase(captured.captureStatus()) || captured.captureId() == null) {
            log.warn("PayPal capture for order {} not completed: {}", orderNumber, captured.captureStatus());
            cancelUnpaidOrder(order);
            cartService.restoreFromOrder(order);
            throw new BadRequestException("PayPal no pudo completar el pago. El pedido fue cancelado; intenta de nuevo.");
        }
        if (captured.amount() == null || captured.amount().compareTo(order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            // the approved amount was checked when the order was created; leave it for manual review
            log.error("Order {} NOT confirmed: PayPal captured {} but order total is {} — review manually",
                    orderNumber, captured.amount(), order.getTotalAmount());
            throw new BadRequestException("El monto del pago no coincide con el total del pedido");
        }

        order.setPaymentId(captured.captureId());
        markPaid(order);
        log.info("Order {} confirmed via PayPal capture {}", orderNumber, captured.captureId());
    }

    public enum AbandonedOutcome { CONFIRMED, CANCELLED, KEPT }

    /**
     * Settles an order that has been PENDING too long. The provider decides: a payment that went
     * through after all confirms the order (e.g. a webhook lost while the server slept); one that
     * never happened is voided and the order cancelled so its stock can be sold again. When the
     * provider cannot be asked, or the payment is still in flight, the order is left for the next run.
     */
    @Transactional
    public AbandonedOutcome resolveAbandonedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) return AbandonedOutcome.KEPT;
        String paymentId = order.getPaymentId();
        try {
            if ("stripe".equalsIgnoreCase(order.getPaymentMethod())) {
                return paymentId == null ? cancelAbandoned(order) : resolveAbandonedStripe(order, paymentId);
            }
            if ("paypal".equalsIgnoreCase(order.getPaymentMethod())) {
                return paymentId == null ? cancelAbandoned(order) : resolveAbandonedPayPal(order, paymentId);
            }
        } catch (StripeException | BadRequestException e) {
            log.warn("Could not check payment of pending order {}, will retry: {}", order.getOrderNumber(), e.getMessage());
        }
        return AbandonedOutcome.KEPT;
    }

    private AbandonedOutcome resolveAbandonedStripe(Order order, String intentId) throws StripeException {
        if (!isStripeConfigured()) return AbandonedOutcome.KEPT;
        StripeIntentSnapshot intent = findStripeIntent(intentId);
        switch (intent.status()) {
            case "succeeded" -> {
                handlePaymentSuccess(intentId, intent.amountReceived(), intent.currency());
                return order.getStatus() == OrderStatus.CONFIRMED ? AbandonedOutcome.CONFIRMED : AbandonedOutcome.KEPT;
            }
            case "processing", "requires_capture" -> {
                return AbandonedOutcome.KEPT;
            }
            case "canceled" -> {
                return cancelAbandoned(order);
            }
            default -> {
                // void it first, so the buyer can no longer pay for an order that is about to be cancelled
                cancelStripeIntent(intentId);
                return cancelAbandoned(order);
            }
        }
    }

    private AbandonedOutcome resolveAbandonedPayPal(Order order, String paypalOrderId) {
        if (!isPayPalConfigured()) return AbandonedOutcome.KEPT;
        Optional<PayPalOrderSnapshot> paypalOrder = findPayPalOrder(paypalOrderId);
        if (paypalOrder.isEmpty()) {
            // a capture id from the old checkout flow; not worth guessing about
            log.warn("Pending order {} has no PayPal order {} — review manually", order.getOrderNumber(), paypalOrderId);
            return AbandonedOutcome.KEPT;
        }
        PayPalOrderSnapshot snapshot = paypalOrder.get();
        if ("COMPLETED".equalsIgnoreCase(snapshot.status())) {
            // captured, but the browser never told us (tab closed mid-payment)
            if (snapshot.captureId() == null || snapshot.amount() == null
                    || snapshot.amount().compareTo(order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                log.error("Order {} NOT confirmed: PayPal captured {} but order total is {} — review manually",
                        order.getOrderNumber(), snapshot.amount(), order.getTotalAmount());
                return AbandonedOutcome.KEPT;
            }
            order.setPaymentId(snapshot.captureId());
            markPaid(order);
            log.info("Pending order {} confirmed: PayPal capture {} found", order.getOrderNumber(), snapshot.captureId());
            return AbandonedOutcome.CONFIRMED;
        }
        // CREATED / APPROVED / VOIDED: nothing was charged, and an uncaptured PayPal order expires on its own
        return cancelAbandoned(order);
    }

    private AbandonedOutcome cancelAbandoned(Order order) {
        cancelUnpaidOrder(order);
        // the shopper can come back and pay without rebuilding the cart (guest carts live in the browser)
        cartService.restoreFromOrder(order);
        log.info("Pending order {} cancelled after going unpaid, stock and cart restored", order.getOrderNumber());
        return AbandonedOutcome.CANCELLED;
    }

    // ── Commissions (for the accounts) ───────────────────────────────────────

    /**
     * Reads the commission Stripe or PayPal charged for an order's payment and stores it; when the
     * provider has no answer yet (or cannot be asked) counts the attempt, so the job retries later
     * and gives up after a few (e.g. a payment made with test keys, unknown to the live account).
     */
    @Transactional
    public void recordPaymentFee(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getPaymentFee() != null) return;
        Optional<BigDecimal> fee;
        try {
            fee = lookupPaymentFee(order.getPaymentMethod(), order.getPaymentId());
        } catch (Exception e) {
            log.warn("Commission of order {} not available: {}", order.getOrderNumber(), e.getMessage());
            fee = Optional.empty();
        }
        if (fee.isPresent()) {
            order.setPaymentFee(fee.get().setScale(2, RoundingMode.HALF_UP));
        } else {
            order.setPaymentFeeAttempts(order.getPaymentFeeAttempts() + 1);
        }
        orderRepository.save(order);
    }

    /** The commission of one payment, in pesos; empty when the provider does not have it (yet). */
    protected Optional<BigDecimal> lookupPaymentFee(String paymentMethod, String paymentId) throws Exception {
        if (paymentId == null || paymentMethod == null) return Optional.empty();
        if ("stripe".equalsIgnoreCase(paymentMethod)) {
            if (!isStripeConfigured()) return Optional.empty();
            PaymentIntent intent = PaymentIntent.retrieve(paymentId, com.stripe.param.PaymentIntentRetrieveParams.builder()
                    .addExpand("latest_charge.balance_transaction").build(), stripeOptions());
            var charge = intent.getLatestChargeObject();
            var balance = charge != null ? charge.getBalanceTransactionObject() : null;
            if (balance == null || balance.getFee() == null) return Optional.empty();
            if (!"mxn".equalsIgnoreCase(balance.getCurrency())) {
                log.warn("Stripe payment {} settled in {}, not MXN: commission not recorded", paymentId, balance.getCurrency());
                return Optional.empty();
            }
            return Optional.of(BigDecimal.valueOf(balance.getFee()).movePointLeft(2));
        }
        if ("paypal".equalsIgnoreCase(paymentMethod)) {
            if (!isPayPalConfigured()) return Optional.empty();
            return paypalClient.getCaptureFee(paymentId);
        }
        return Optional.empty();
    }

    public record StripeIntentSnapshot(String status, long amountReceived, String currency) {}

    protected StripeIntentSnapshot findStripeIntent(String paymentIntentId) throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, stripeOptions());
        return new StripeIntentSnapshot(intent.getStatus(),
                intent.getAmountReceived() != null ? intent.getAmountReceived() : 0L, intent.getCurrency());
    }

    protected void cancelStripeIntent(String paymentIntentId) throws StripeException {
        PaymentIntent.retrieve(paymentIntentId, stripeOptions())
                .cancel(com.stripe.param.PaymentIntentCancelParams.builder().build(), stripeOptions());
    }

    /** PENDING → CONFIRMED once the payment is in; the shopper is emailed after the transaction commits. */
    private void markPaid(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        order.markPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        events.publishEvent(new OrderPaidEvent(order.getId()));
    }

    private void cancelUnpaidOrder(Order order) {
        stockService.restore(order, order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional
    public void handlePayPalCaptureCompleted(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            // like Stripe's: a resent or late event must not move a shipped order back to CONFIRMED
            if (order.getStatus() != OrderStatus.PENDING) {
                log.info("Order {} is already {}, skipping", order.getOrderNumber(), order.getStatus());
                return;
            }
            markPaid(order);
            log.info("Order {} confirmed via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.COMPLETED — no order found for captureId={}", captureId));
    }

    @Transactional
    public void handlePayPalCaptureDenied(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) return;
            stockService.restore(order, order.getStatus());
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("Order {} cancelled via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.DENIED — no order found for captureId={}", captureId));
    }

    @Transactional
    public void handlePayPalCaptureRefunded(String captureId) {
        orderRepository.findByPaymentId(captureId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.REFUNDED) return;
            stockService.restore(order, order.getStatus());
            order.setStatus(OrderStatus.REFUNDED);
            order.markRefundedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Order {} refunded via PayPal webhook captureId={}", order.getOrderNumber(), captureId);
        }, () -> log.warn("PayPal webhook CAPTURE.REFUNDED — no order found for captureId={}", captureId));
    }


    public Map<String, Object> createPayPalOrder(BigDecimal amount) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal no está configurado. Define PAYPAL_CLIENT_ID y PAYPAL_CLIENT_SECRET.");
        }

        try {
            PayPalOrderSnapshot order = paypalClient.createOrder(amount);
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

    /**
     * Legacy flow: captures before the order exists. Kept so checkout pages loaded before the
     * capture moved after order creation keep working; the current checkout uses capturePayPalForOrder.
     */
    public Map<String, Object> capturePayPalOrder(String orderId) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal no está configurado.");
        }
        try {
            PayPalOrderSnapshot order = capturePayPal(orderId);
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.id());
            result.put("status", order.status());
            result.put("captureId", order.captureId());
            log.info("PayPal order captured: {} status: {}", order.id(), order.status());
            return result;
        } catch (IOException e) {
            log.error("PayPal capture order error: {}", e.getMessage());
            throw new RuntimeException("Failed to capture PayPal order: " + e.getMessage());
        }
    }

    /** What we need from a PayPal order; the capture fields are set once it has been captured. */
    public record PayPalOrderSnapshot(String id, String status, String currency, BigDecimal amount,
                                         String captureId, String captureStatus) {}

    /** The PayPal order with this id, or empty if PayPal has none (e.g. the id is a capture id). */
    protected Optional<PayPalOrderSnapshot> findPayPalOrder(String paypalOrderId) {
        if (!isPayPalConfigured()) {
            throw new BadRequestException("PayPal no está configurado.");
        }
        try {
            return paypalClient.getOrder(paypalOrderId);
        } catch (IOException e) {
            log.error("PayPal error fetching order {}: {}", paypalOrderId, e.getMessage());
            throw new BadRequestException("No se pudo verificar el pago con PayPal");
        }
    }

    /** Captures an approved PayPal order; throws if PayPal refuses (e.g. the buyer's funding failed). */
    protected PayPalOrderSnapshot capturePayPal(String paypalOrderId) throws IOException {
        return paypalClient.captureOrder(paypalOrderId);
    }
}
