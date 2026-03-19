package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductColor;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.Capture;
import com.paypal.orders.OrderActionRequest;
import com.paypal.orders.OrdersCaptureRequest;
import com.paypal.orders.OrdersCreateRequest;
import com.paypal.orders.OrdersGetRequest;
import com.paypal.orders.PaymentCollection;
import com.paypal.orders.PurchaseUnit;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSizeRepository productSizeRepository;

    @InjectMocks private PaymentService paymentService;

    @BeforeEach
    void injectValues() {
        ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "sk_test_abc123");
        ReflectionTestUtils.setField(paymentService, "webhookSecret",   "whsec_test_secret");
        ReflectionTestUtils.setField(paymentService, "paypalClientId",     "");
        ReflectionTestUtils.setField(paymentService, "paypalClientSecret", "");
        ReflectionTestUtils.setField(paymentService, "paypalMode",         "sandbox");
        // paypalClient stays null — PayPal not configured by default in most tests
    }

    // ─── isStripeConfigured ───────────────────────────────────────────────────

    @Nested
    @DisplayName("isStripeConfigured")
    class IsStripeConfigured {

        @Test
        @DisplayName("returns true when secret key is present")
        void isStripeConfigured_withKey() {
            assertThat(paymentService.isStripeConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns false when secret key is blank")
        void isStripeConfigured_blankKey() {
            ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "");
            assertThat(paymentService.isStripeConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns false when secret key is null")
        void isStripeConfigured_nullKey() {
            ReflectionTestUtils.setField(paymentService, "stripeSecretKey", null);
            assertThat(paymentService.isStripeConfigured()).isFalse();
        }
    }

    // ─── isPayPalConfigured ───────────────────────────────────────────────────

    @Nested
    @DisplayName("isPayPalConfigured")
    class IsPayPalConfigured {

        @Test
        @DisplayName("returns false when paypalClient is null")
        void isPayPalConfigured_notConfigured() {
            assertThat(paymentService.isPayPalConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns true when paypalClient is injected")
        void isPayPalConfigured_configured() {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);
            assertThat(paymentService.isPayPalConfigured()).isTrue();
        }
    }

    // ─── getPayPalClientId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPayPalClientId")
    class GetPayPalClientId {

        @Test
        @DisplayName("returns the configured client id string")
        void getPayPalClientId_returnsValue() {
            ReflectionTestUtils.setField(paymentService, "paypalClientId", "PAYPAL_CLIENT_XYZ");
            assertThat(paymentService.getPayPalClientId()).isEqualTo("PAYPAL_CLIENT_XYZ");
        }
    }

    // ─── createPaymentIntent ──────────────────────────────────────────────────

    @Nested
    @DisplayName("createPaymentIntent")
    class CreatePaymentIntent {

        @Test
        @DisplayName("throws BadRequestException when Stripe is not configured")
        void createPaymentIntent_stripeNotConfigured() {
            ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "");

            assertThatThrownBy(() -> paymentService.createPaymentIntent(BigDecimal.TEN, "usd"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");
        }

        @Test
        @DisplayName("returns clientSecret and paymentIntentId on success")
        void createPaymentIntent_success() throws StripeException {
            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getClientSecret()).thenReturn("pi_secret_xyz");
            when(mockIntent.getId()).thenReturn("pi_abc123");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenReturn(mockIntent);

                Map<String, String> result = paymentService.createPaymentIntent(
                        BigDecimal.valueOf(99.99), "usd");

                assertThat(result).containsEntry("clientSecret", "pi_secret_xyz");
                assertThat(result).containsEntry("paymentIntentId", "pi_abc123");
            }
        }

        @Test
        @DisplayName("wraps StripeException in RuntimeException")
        void createPaymentIntent_stripeException() throws StripeException {
            StripeException stripeEx = mock(StripeException.class);
            when(stripeEx.getMessage()).thenReturn("card_declined");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenThrow(stripeEx);

                assertThatThrownBy(() -> paymentService.createPaymentIntent(BigDecimal.TEN, "usd"))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Payment processing failed");
            }
        }

        @Test
        @DisplayName("converts amount to cents (multiplies by 100)")
        void createPaymentIntent_convertsToCents() throws StripeException {
            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getClientSecret()).thenReturn("secret");
            when(mockIntent.getId()).thenReturn("pi_1");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenAnswer(inv -> {
                            PaymentIntentCreateParams params = inv.getArgument(0);
                            // 25.00 * 100 = 2500
                            assertThat(params.getAmount()).isEqualTo(2500L);
                            return mockIntent;
                        });

                paymentService.createPaymentIntent(BigDecimal.valueOf(25.00), "mxn");
            }
        }

        @Test
        @DisplayName("lowercases currency code before sending to Stripe")
        void createPaymentIntent_lowercasesCurrency() throws StripeException {
            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getClientSecret()).thenReturn("s");
            when(mockIntent.getId()).thenReturn("pi_x");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenAnswer(inv -> {
                            PaymentIntentCreateParams params = inv.getArgument(0);
                            assertThat(params.getCurrency()).isEqualTo("usd");
                            return mockIntent;
                        });

                paymentService.createPaymentIntent(BigDecimal.TEN, "USD");
            }
        }
    }

    // ─── constructWebhookEvent ────────────────────────────────────────────────

    @Nested
    @DisplayName("constructWebhookEvent")
    class ConstructWebhookEvent {

        @Test
        @DisplayName("delegates to Webhook.constructEvent with correct params")
        void constructWebhookEvent_success() throws SignatureVerificationException {
            Event mockEvent = mock(Event.class);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(
                        eq("payload"), eq("sig_header"), eq("whsec_test_secret")))
                        .thenReturn(mockEvent);

                Event result = paymentService.constructWebhookEvent("payload", "sig_header");

                assertThat(result).isSameAs(mockEvent);
            }
        }

        @Test
        @DisplayName("propagates SignatureVerificationException when signature is invalid")
        void constructWebhookEvent_badSignature() throws SignatureVerificationException {
            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenThrow(new SignatureVerificationException("bad sig", "sig_header"));

                assertThatThrownBy(() ->
                        paymentService.constructWebhookEvent("tampered_payload", "bad_sig"))
                        .isInstanceOf(SignatureVerificationException.class);
            }
        }
    }

    // ─── handlePaymentSuccess ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentSuccess")
    class HandlePaymentSuccess {

        @Test
        @DisplayName("confirms a pending order and saves it")
        void handlePaymentSuccess_confirmsPendingOrder() {
            Order order = Order.builder()
                    .orderNumber("ORD-001")
                    .status(OrderStatus.PENDING)
                    .build();
            when(orderRepository.findByPaymentId("pi_001")).thenReturn(Optional.of(order));

            paymentService.handlePaymentSuccess("pi_001");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("does nothing when no order found for paymentIntentId")
        void handlePaymentSuccess_orderNotFound() {
            when(orderRepository.findByPaymentId("pi_ghost")).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    paymentService.handlePaymentSuccess("pi_ghost"));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save when order is already CONFIRMED")
        void handlePaymentSuccess_alreadyConfirmed() {
            Order order = Order.builder()
                    .orderNumber("ORD-002")
                    .status(OrderStatus.CONFIRMED)
                    .build();
            when(orderRepository.findByPaymentId("pi_002")).thenReturn(Optional.of(order));

            paymentService.handlePaymentSuccess("pi_002");

            verify(orderRepository, never()).save(any());
        }
    }

    // ─── handlePaymentFailure ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentFailure")
    class HandlePaymentFailure {

        @Test
        @DisplayName("cancels order and restores stock for simple (no-color) product")
        void handlePaymentFailure_restoresSimpleProductStock() {
            Product product = Product.builder()
                    .id(1L)
                    .name("Shirt")
                    .stockQuantity(5)
                    .colors(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(3)
                    .build();

            Order order = Order.builder()
                    .orderNumber("ORD-003")
                    .status(OrderStatus.PENDING)
                    .items(new ArrayList<>(List.of(item)))
                    .build();

            when(orderRepository.findByPaymentId("pi_003")).thenReturn(Optional.of(order));

            paymentService.handlePaymentFailure("pi_003");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(product.getStockQuantity()).isEqualTo(8);
            verify(productRepository).save(product);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("restores size stock when product has colors and selectedSize is set")
        void handlePaymentFailure_restoresSizeStock() {
            ProductColor color = new ProductColor();
            color.setId(10L);

            ProductSize size = ProductSize.builder()
                    .id(20L)
                    .stock(2)
                    .build();

            Product product = Product.builder()
                    .id(2L)
                    .name("Jeans")
                    .stockQuantity(0)
                    .colors(new ArrayList<>(List.of(color)))
                    .build();

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .selectedSize(size)
                    .quantity(4)
                    .build();

            Order order = Order.builder()
                    .orderNumber("ORD-004")
                    .status(OrderStatus.PENDING)
                    .items(new ArrayList<>(List.of(item)))
                    .build();

            when(orderRepository.findByPaymentId("pi_004")).thenReturn(Optional.of(order));

            paymentService.handlePaymentFailure("pi_004");

            assertThat(size.getStock()).isEqualTo(6);
            verify(productSizeRepository).save(size);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("skips stock restore for items without a product")
        void handlePaymentFailure_nullProductItem() {
            OrderItem item = OrderItem.builder()
                    .product(null)
                    .quantity(1)
                    .build();

            Order order = Order.builder()
                    .orderNumber("ORD-005")
                    .status(OrderStatus.PENDING)
                    .items(new ArrayList<>(List.of(item)))
                    .build();

            when(orderRepository.findByPaymentId("pi_005")).thenReturn(Optional.of(order));

            assertThatNoException().isThrownBy(() ->
                    paymentService.handlePaymentFailure("pi_005"));

            verify(productRepository, never()).save(any());
            verify(productSizeRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when no order found")
        void handlePaymentFailure_orderNotFound() {
            when(orderRepository.findByPaymentId("pi_nope")).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    paymentService.handlePaymentFailure("pi_nope"));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save when order is already CANCELLED")
        void handlePaymentFailure_alreadyCancelled() {
            Order order = Order.builder()
                    .orderNumber("ORD-006")
                    .status(OrderStatus.CANCELLED)
                    .items(new ArrayList<>())
                    .build();
            when(orderRepository.findByPaymentId("pi_006")).thenReturn(Optional.of(order));

            paymentService.handlePaymentFailure("pi_006");

            verify(orderRepository, never()).save(any());
        }
    }

    // ─── handleRefund ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleRefund")
    class HandleRefund {

        @Test
        @DisplayName("sets status to REFUNDED and restores stock for simple product")
        void handleRefund_restoresSimpleProductStock() {
            Product product = Product.builder()
                    .id(3L)
                    .name("Hat")
                    .stockQuantity(10)
                    .colors(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(2)
                    .build();

            Order order = Order.builder()
                    .orderNumber("ORD-007")
                    .status(OrderStatus.DELIVERED)
                    .items(new ArrayList<>(List.of(item)))
                    .build();

            when(orderRepository.findByPaymentId("pi_007")).thenReturn(Optional.of(order));

            paymentService.handleRefund("pi_007");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(product.getStockQuantity()).isEqualTo(12);
            verify(productRepository).save(product);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("does nothing when order not found")
        void handleRefund_orderNotFound() {
            when(orderRepository.findByPaymentId("pi_absent")).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    paymentService.handleRefund("pi_absent"));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save when order is already REFUNDED")
        void handleRefund_alreadyRefunded() {
            Order order = Order.builder()
                    .orderNumber("ORD-008")
                    .status(OrderStatus.REFUNDED)
                    .items(new ArrayList<>())
                    .build();
            when(orderRepository.findByPaymentId("pi_008")).thenReturn(Optional.of(order));

            paymentService.handleRefund("pi_008");

            verify(orderRepository, never()).save(any());
        }
    }

    // ─── confirmPayPalPayment ─────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmPayPalPayment")
    class ConfirmPayPalPayment {

        @Test
        @DisplayName("sets paymentId, status CONFIRMED and saves")
        void confirmPayPalPayment_success() {
            Order order = Order.builder()
                    .orderNumber("ORD-PP01")
                    .status(OrderStatus.PENDING)
                    .build();
            when(orderRepository.findByOrderNumber("ORD-PP01")).thenReturn(Optional.of(order));

            paymentService.confirmPayPalPayment("ORD-PP01", "CAPTURE-001");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("CAPTURE-001");
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("does not save when order is already CONFIRMED")
        void confirmPayPalPayment_alreadyConfirmed() {
            Order order = Order.builder()
                    .orderNumber("ORD-PP02")
                    .status(OrderStatus.CONFIRMED)
                    .build();
            when(orderRepository.findByOrderNumber("ORD-PP02")).thenReturn(Optional.of(order));

            paymentService.confirmPayPalPayment("ORD-PP02", "CAPTURE-002");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws RuntimeException when order is not found")
        void confirmPayPalPayment_orderNotFound() {
            when(orderRepository.findByOrderNumber("ORD-GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.confirmPayPalPayment("ORD-GHOST", "CAPTURE-X"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // ─── createPayPalOrder ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPayPalOrder")
    class CreatePayPalOrder {

        @Test
        @DisplayName("throws BadRequestException when PayPal is not configured")
        void createPayPalOrder_notConfigured() {
            assertThatThrownBy(() ->
                    paymentService.createPayPalOrder(BigDecimal.valueOf(50)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PayPal is not configured");
        }

        @Test
        @DisplayName("returns orderId and status from PayPal API response")
        void createPayPalOrder_success() throws IOException {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);

            com.paypal.orders.Order ppOrder = mock(com.paypal.orders.Order.class);
            when(ppOrder.id()).thenReturn("PAYPAL-ORDER-123");
            when(ppOrder.status()).thenReturn("CREATED");

            @SuppressWarnings("unchecked")
            HttpResponse<com.paypal.orders.Order> mockResponse = mock(HttpResponse.class);
            when(mockResponse.result()).thenReturn(ppOrder);
            when(mockClient.execute(any(OrdersCreateRequest.class))).thenReturn(mockResponse);

            Map<String, Object> result = paymentService.createPayPalOrder(BigDecimal.valueOf(75.50));

            assertThat(result).containsEntry("orderId", "PAYPAL-ORDER-123");
            assertThat(result).containsEntry("status", "CREATED");
        }

        @Test
        @DisplayName("wraps IOException in RuntimeException")
        void createPayPalOrder_ioException() throws IOException {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);

            when(mockClient.execute(any(OrdersCreateRequest.class)))
                    .thenThrow(new IOException("connection refused"));

            assertThatThrownBy(() -> paymentService.createPayPalOrder(BigDecimal.TEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create PayPal order");
        }
    }

    // ─── capturePayPalOrder ───────────────────────────────────────────────────

    @Nested
    @DisplayName("capturePayPalOrder")
    class CapturePayPalOrder {

        @Test
        @DisplayName("throws BadRequestException when PayPal is not configured")
        void capturePayPalOrder_notConfigured() {
            assertThatThrownBy(() ->
                    paymentService.capturePayPalOrder("PAYPAL-ORDER-XYZ"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PayPal is not configured");
        }

        @Test
        @DisplayName("returns orderId, status, and captureId from PayPal response")
        void capturePayPalOrder_success() throws IOException {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);

            Capture capture = mock(Capture.class);
            when(capture.id()).thenReturn("CAPTURE-ABC");

            PaymentCollection payments = mock(PaymentCollection.class);
            when(payments.captures()).thenReturn(List.of(capture));

            PurchaseUnit purchaseUnit = mock(PurchaseUnit.class);
            when(purchaseUnit.payments()).thenReturn(payments);

            com.paypal.orders.Order ppOrder = mock(com.paypal.orders.Order.class);
            when(ppOrder.id()).thenReturn("PAYPAL-ORDER-456");
            when(ppOrder.status()).thenReturn("COMPLETED");
            when(ppOrder.purchaseUnits()).thenReturn(List.of(purchaseUnit));

            @SuppressWarnings("unchecked")
            HttpResponse<com.paypal.orders.Order> mockResponse = mock(HttpResponse.class);
            when(mockResponse.result()).thenReturn(ppOrder);
            when(mockClient.execute(any(OrdersCaptureRequest.class))).thenReturn(mockResponse);

            Map<String, Object> result = paymentService.capturePayPalOrder("PAYPAL-ORDER-456");

            assertThat(result).containsEntry("orderId", "PAYPAL-ORDER-456");
            assertThat(result).containsEntry("status", "COMPLETED");
            assertThat(result).containsEntry("captureId", "CAPTURE-ABC");
        }

        @Test
        @DisplayName("returns null captureId when purchaseUnits is empty")
        void capturePayPalOrder_noCaptureId() throws IOException {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);

            com.paypal.orders.Order ppOrder = mock(com.paypal.orders.Order.class);
            when(ppOrder.id()).thenReturn("PAYPAL-ORDER-789");
            when(ppOrder.status()).thenReturn("COMPLETED");
            when(ppOrder.purchaseUnits()).thenReturn(List.of());

            @SuppressWarnings("unchecked")
            HttpResponse<com.paypal.orders.Order> mockResponse = mock(HttpResponse.class);
            when(mockResponse.result()).thenReturn(ppOrder);
            when(mockClient.execute(any(OrdersCaptureRequest.class))).thenReturn(mockResponse);

            Map<String, Object> result = paymentService.capturePayPalOrder("PAYPAL-ORDER-789");

            assertThat(result).containsEntry("captureId", null);
        }

        @Test
        @DisplayName("wraps IOException in RuntimeException")
        void capturePayPalOrder_ioException() throws IOException {
            PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
            ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);

            when(mockClient.execute(any(OrdersCaptureRequest.class)))
                    .thenThrow(new IOException("timeout"));

            assertThatThrownBy(() -> paymentService.capturePayPalOrder("PAYPAL-ORDER-FAIL"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to capture PayPal order");
        }
    }
}
