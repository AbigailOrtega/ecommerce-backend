package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.ecommerce.service.PaymentService.AbandonedOutcome;
import com.ecommerce.service.PaymentService.PayPalOrderSnapshot;
import com.ecommerce.service.PaymentService.StripeIntentSnapshot;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService.resolveAbandonedOrder")
class AbandonedOrderResolutionTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductSizeRepository productSizeRepository;
    @Mock CartService cartService;

    /** Stands in for Stripe and PayPal: tests set what each provider answers. */
    static class ProviderStub extends PaymentService {
        StripeIntentSnapshot intent;
        StripeException stripeDown;
        final List<String> cancelledIntents = new ArrayList<>();
        Optional<PayPalOrderSnapshot> paypalOrder = Optional.empty();
        boolean paypalDown;

        ProviderStub(OrderRepository orders, StockService stock, CartService cart) {
            super(orders, stock, cart, null, event -> {});
        }

        @Override public boolean isPayPalConfigured() { return true; }

        @Override protected StripeIntentSnapshot findStripeIntent(String id) throws StripeException {
            if (stripeDown != null) throw stripeDown;
            return intent;
        }

        @Override protected void cancelStripeIntent(String id) { cancelledIntents.add(id); }

        @Override protected Optional<PayPalOrderSnapshot> findPayPalOrder(String id) {
            if (paypalDown) throw new BadRequestException("No se pudo verificar el pago con PayPal");
            return paypalOrder;
        }
    }

    ProviderStub service;

    @BeforeEach
    void setUp() {
        service = new ProviderStub(orderRepository, new StockService(productRepository, productSizeRepository), cartService);
        com.ecommerce.credentials.StoreKeys keys = mock(com.ecommerce.credentials.StoreKeys.class);
        lenient().when(keys.stripe()).thenReturn(new com.ecommerce.credentials.StoreKeys.Stripe("pk_test_x", "sk_test_abc", null));
        ReflectionTestUtils.setField(service, "storeKeys", keys);
    }

    private Order pending(String method, String paymentId) {
        Product product = Product.builder().id(9L).name("Cojín").stockQuantity(3).colors(new ArrayList<>()).build();
        Order order = Order.builder().id(1L).orderNumber("ORD-1").status(OrderStatus.PENDING)
                .paymentMethod(method).paymentId(paymentId).totalAmount(new BigDecimal("250.00"))
                .items(new ArrayList<>(List.of(OrderItem.builder().product(product).quantity(2).build())))
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        return order;
    }

    private void assertCancelledWithStockBack(Order order, AbandonedOutcome outcome) {
        assertThat(outcome).isEqualTo(AbandonedOutcome.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productRepository).incrementStock(9L, 2);
    }

    @Nested
    @DisplayName("Stripe")
    class Stripe {

        @Test
        @DisplayName("an intent that was never paid is voided and the order cancelled")
        void unpaid() {
            Order order = pending("stripe", "pi_1");
            service.intent = new StripeIntentSnapshot("requires_payment_method", 0, "mxn");

            AbandonedOutcome outcome = service.resolveAbandonedOrder(1L);

            assertThat(service.cancelledIntents).containsExactly("pi_1");
            assertCancelledWithStockBack(order, outcome);
            // the items go back to the shopper's cart so they can pay later
            verify(cartService).restoreFromOrder(order);
        }

        @Test
        @DisplayName("a payment that succeeded but whose webhook was lost confirms the order")
        void paidButWebhookLost() {
            Order order = pending("stripe", "pi_2");
            when(orderRepository.findByPaymentId("pi_2")).thenReturn(Optional.of(order));
            service.intent = new StripeIntentSnapshot("succeeded", 25000, "mxn");

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.CONFIRMED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(service.cancelledIntents).isEmpty();
            verify(productRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("a payment still processing is left alone")
        void processing() {
            Order order = pending("stripe", "pi_3");
            service.intent = new StripeIntentSnapshot("processing", 0, "mxn");

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(service.cancelledIntents).isEmpty();
            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("when Stripe cannot be reached nothing changes")
        void stripeDown() {
            Order order = pending("stripe", "pi_4");
            service.stripeDown = new ApiConnectionException("timeout");

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(productRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("an intent already cancelled just cancels the order")
        void intentAlreadyCancelled() {
            Order order = pending("stripe", "pi_5");
            service.intent = new StripeIntentSnapshot("canceled", 0, "mxn");

            AbandonedOutcome outcome = service.resolveAbandonedOrder(1L);

            assertThat(service.cancelledIntents).isEmpty();
            assertCancelledWithStockBack(order, outcome);
        }
    }

    @Nested
    @DisplayName("PayPal")
    class PayPal {

        @Test
        @DisplayName("an approved but never captured PayPal order cancels the order")
        void approvedNotCaptured() {
            Order order = pending("paypal", "PP-1");
            service.paypalOrder = Optional.of(new PayPalOrderSnapshot("PP-1", "APPROVED", "MXN", new BigDecimal("250.00"), null, null));

            assertCancelledWithStockBack(order, service.resolveAbandonedOrder(1L));
            verify(cartService).restoreFromOrder(order);
        }

        @Test
        @DisplayName("a capture the browser never reported confirms the order with the capture id")
        void capturedButNotReported() {
            Order order = pending("paypal", "PP-2");
            service.paypalOrder = Optional.of(new PayPalOrderSnapshot("PP-2", "COMPLETED", "MXN", new BigDecimal("250.00"), "CAP-9", "COMPLETED"));

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.CONFIRMED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("CAP-9");
        }

        @Test
        @DisplayName("a capture for a different amount is left for manual review")
        void capturedWrongAmount() {
            Order order = pending("paypal", "PP-3");
            service.paypalOrder = Optional.of(new PayPalOrderSnapshot("PP-3", "COMPLETED", "MXN", new BigDecimal("1.00"), "CAP-1", "COMPLETED"));

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("when PayPal cannot be reached nothing changes")
        void paypalDown() {
            Order order = pending("paypal", "PP-4");
            service.paypalDown = true;

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("an id PayPal does not know (old checkout flow) is left for manual review")
        void unknownToPayPal() {
            Order order = pending("paypal", "CAPTURE-ID-FROM-OLD-FLOW");

            assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @Test
    @DisplayName("orders that are no longer pending are not touched")
    void notPending() {
        Order order = pending("stripe", "pi_6");
        order.setStatus(OrderStatus.CONFIRMED);

        assertThat(service.resolveAbandonedOrder(1L)).isEqualTo(AbandonedOutcome.KEPT);
        verify(orderRepository, never()).save(any());
    }
}
