package com.ecommerce.e2e;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService;
import com.ecommerce.service.CartService;
import com.ecommerce.service.PayPalClient;
import com.ecommerce.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Stripe and PayPal in the Playwright suite (profile "fake-payments", which only
 * exists on the test classpath, so this class can never reach production). Only the calls to the
 * providers are faked; order creation, payment verification rules, webhooks and stock are real.
 */
@Slf4j
@Service
@Primary
@Profile("fake-payments")
public class FakePaymentService extends PaymentService {

    private final Map<String, Long> stripeIntents = new ConcurrentHashMap<>();   // id → cents
    private final Map<String, PayPalOrderSnapshot> paypalOrders = new ConcurrentHashMap<>();
    /**
     * Buyers (by email) whose next PayPal capture is declined. Per buyer, not "the next capture":
     * tests run in parallel, and a global switch was taken by whichever test captured first.
     */
    private final java.util.Set<String> declineCaptureFor = ConcurrentHashMap.newKeySet();
    private final OrderRepository orders;
    private final Map<String, Long> paypalCaptures = new ConcurrentHashMap<>();  // id → cents

    public FakePaymentService(OrderRepository orderRepository, StockService stockService, CartService cartService,
                              PayPalClient paypalClient, ApplicationEventPublisher events) {
        super(orderRepository, stockService, cartService, paypalClient, events);
        this.orders = orderRepository;
        log.warn("[e2e] FakePaymentService active: Stripe and PayPal are simulated");
    }

    @Override public boolean isStripeConfigured() { return true; }
    @Override public boolean isPayPalConfigured() { return true; }
    @Override public String getPayPalClientId() { return "e2e-fake-paypal"; }

    @Override
    public Map<String, String> createPaymentIntent(BigDecimal amount, String currency) {
        String id = "pi_e2e_" + UUID.randomUUID().toString().replace("-", "");
        stripeIntents.put(id, cents(amount));
        return Map.of("clientSecret", id + "_secret_e2e", "paymentIntentId", id);
    }

    @Override
    protected void verifyStripeIntentForOrder(String paymentIntentId, BigDecimal orderTotal) {
        if (!stripeIntents.containsKey(paymentIntentId)) {
            throw new BadRequestException("No se pudo verificar el pago con Stripe");
        }
        // like the real one, an unconfirmed intent is brought in line with the server's total
        stripeIntents.put(paymentIntentId, cents(orderTotal));
    }

    @Override
    public Map<String, Object> createPayPalOrder(BigDecimal amount) {
        String id = "PAYPAL-E2E-" + UUID.randomUUID();
        // the buyer approves in the (fake) PayPal popup right after
        paypalOrders.put(id, new PayPalOrderSnapshot(id, "APPROVED", "MXN", scale(amount), null, null));
        return Map.of("orderId", id, "status", "CREATED");
    }

    @Override
    protected Optional<PayPalOrderSnapshot> findPayPalOrder(String paypalOrderId) {
        return Optional.ofNullable(paypalOrders.get(paypalOrderId));
    }

    @Override
    protected PayPalOrderSnapshot capturePayPal(String paypalOrderId) throws IOException {
        PayPalOrderSnapshot order = paypalOrders.get(paypalOrderId);
        if (order == null || !"APPROVED".equals(order.status())) throw new IOException("ORDER_NOT_APPROVED");
        String buyer = orders.findByPaymentId(paypalOrderId)
                .map(o -> o.getGuestEmail() != null ? o.getGuestEmail() : o.getUser().getEmail())
                .map(e -> e.toLowerCase())
                .orElse(null);
        if (buyer != null && declineCaptureFor.remove(buyer)) throw new IOException("INSTRUMENT_DECLINED");
        String captureId = "CAP-E2E-" + UUID.randomUUID();
        PayPalOrderSnapshot captured = new PayPalOrderSnapshot(paypalOrderId, "COMPLETED", "MXN", order.amount(), captureId, "COMPLETED");
        paypalOrders.put(paypalOrderId, captured);
        paypalCaptures.put(captureId, cents(order.amount()));
        return captured;
    }

    /** Makes PayPal decline this buyer's next capture, as when their funding source fails. */
    public void declinePayPalCaptureFor(String email) {
        declineCaptureFor.add(email.toLowerCase());
    }

    @Override
    protected void verifyPayPalCapture(String captureId, BigDecimal orderTotal) {
        Long captured = paypalCaptures.get(captureId);
        if (captured == null) throw new BadRequestException("El pago de PayPal no está completado");
        if (captured != cents(orderTotal)) {
            throw new BadRequestException("El monto del pago no coincide con el total del pedido");
        }
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static long cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
