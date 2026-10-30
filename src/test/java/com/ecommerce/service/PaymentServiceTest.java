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
        ReflectionTestUtils.setField(paymentService, "stockService",
                new StockService(productRepository, productSizeRepository));
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
                    .totalAmount(new BigDecimal("150.00"))
                    .build();
            when(orderRepository.findByPaymentId("pi_001")).thenReturn(Optional.of(order));

            paymentService.handlePaymentSuccess("pi_001", 15000L, "mxn");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("does not confirm when the amount received differs from the order total")
        void handlePaymentSuccess_amountMismatch() {
            Order order = Order.builder()
                    .orderNumber("ORD-003")
                    .status(OrderStatus.PENDING)
                    .totalAmount(new BigDecimal("10000.00"))
                    .build();
            when(orderRepository.findByPaymentId("pi_003")).thenReturn(Optional.of(order));

            paymentService.handlePaymentSuccess("pi_003", 100L, "mxn");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not confirm when the currency is not MXN")
        void handlePaymentSuccess_wrongCurrency() {
            Order order = Order.builder()
                    .orderNumber("ORD-004")
                    .status(OrderStatus.PENDING)
                    .totalAmount(new BigDecimal("150.00"))
                    .build();
            when(orderRepository.findByPaymentId("pi_004")).thenReturn(Optional.of(order));

            paymentService.handlePaymentSuccess("pi_004", 15000L, "usd");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when no order found for paymentIntentId")
        void handlePaymentSuccess_orderNotFound() {
            when(orderRepository.findByPaymentId("pi_ghost")).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    paymentService.handlePaymentSuccess("pi_ghost", 15000L, "mxn"));

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

            paymentService.handlePaymentSuccess("pi_002", 15000L, "mxn");

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
            verify(productRepository).incrementStock(1L, 3);
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

            verify(productSizeRepository).incrementStock(20L, 4);
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

            verifyNoInteractions(productRepository, productSizeRepository);
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
        @DisplayName("does not restore stock again when the order was already CANCELLED")
        void handleRefund_afterCancel_noDoubleRestore() {
            Product product = Product.builder().id(7L).name("Hat").stockQuantity(1).colors(new ArrayList<>()).build();
            Order order = Order.builder()
                    .orderNumber("ORD-DBL")
                    .status(OrderStatus.CANCELLED)
                    .items(new ArrayList<>(List.of(OrderItem.builder().product(product).quantity(2).build())))
                    .build();
            when(orderRepository.findByPaymentId("pi_dbl")).thenReturn(Optional.of(order));

            paymentService.handleRefund("pi_dbl");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            verifyNoInteractions(productRepository, productSizeRepository);
        }

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
            verify(productRepository).incrementStock(3L, 2);
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
        @DisplayName("confirms the order when its own capture is COMPLETED for the order total")
        void confirmPayPalPayment_success() throws IOException {
            mockPayPalCapture("CAPTURE-001", "COMPLETED", "MXN", "150.00");
            Order order = paypalOrder("ORD-PP01", "CAPTURE-001", "150.00");
            when(orderRepository.findByOrderNumber("ORD-PP01")).thenReturn(Optional.of(order));

            paymentService.confirmPayPalPayment("ORD-PP01", "CAPTURE-001");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("CAPTURE-001");
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("rejects a capture id that is not the one attached to the order")
        void confirmPayPalPayment_foreignCapture() {
            Order order = paypalOrder("ORD-PP03", "CAPTURE-REAL", "150.00");
            when(orderRepository.findByOrderNumber("ORD-PP03")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.confirmPayPalPayment("ORD-PP03", "CAPTURE-FAKE"))
                    .isInstanceOf(BadRequestException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects when PayPal reports the capture is not COMPLETED")
        void confirmPayPalPayment_captureNotCompleted() throws IOException {
            mockPayPalCapture("CAPTURE-004", "PENDING", "MXN", "150.00");
            Order order = paypalOrder("ORD-PP04", "CAPTURE-004", "150.00");
            when(orderRepository.findByOrderNumber("ORD-PP04")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.confirmPayPalPayment("ORD-PP04", "CAPTURE-004"))
                    .isInstanceOf(BadRequestException.class);
            verify(orderRepository, never()).save(any());
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

    // ─── verification helpers ─────────────────────────────────────────────────

    private Order paypalOrder(String orderNumber, String captureId, String total) {
        return Order.builder()
                .orderNumber(orderNumber)
                .status(OrderStatus.PENDING)
                .paymentMethod("paypal")
                .paymentId(captureId)
                .totalAmount(new BigDecimal(total))
                .build();
    }

    private void mockPayPalCapture(String captureId, String status, String currency, String value) throws IOException {
        PayPalHttpClient mockClient = mock(PayPalHttpClient.class);
        ReflectionTestUtils.setField(paymentService, "paypalClient", mockClient);
        com.paypal.payments.Capture capture = new com.paypal.payments.Capture()
                .id(captureId)
                .status(status)
                .amount(new com.paypal.payments.Money().currencyCode(currency).value(value));
        @SuppressWarnings("unchecked")
        HttpResponse<com.paypal.payments.Capture> response = mock(HttpResponse.class);
        when(response.result()).thenReturn(capture);
        when(mockClient.execute(any(com.paypal.payments.CapturesGetRequest.class))).thenReturn(response);
        // a capture id is not an order id: PayPal answers 404, so the legacy capture check runs
        lenient().when(mockClient.execute(any(OrdersGetRequest.class)))
                .thenThrow(new com.paypal.http.exceptions.HttpException("not found", 404, new com.paypal.http.Headers()));
    }

    /** A PayPal order as returned by OrdersGetRequest / OrdersCaptureRequest. */
    private static com.paypal.orders.Order paypalApiOrder(String id, String status, String value,
                                                          String captureId, String captureStatus) {
        PurchaseUnit unit = new PurchaseUnit()
                .amountWithBreakdown(new com.paypal.orders.AmountWithBreakdown().currencyCode("MXN").value(value));
        if (captureId != null) {
            unit.payments(new PaymentCollection().captures(List.of(new Capture().id(captureId).status(captureStatus)
                    .amount(new com.paypal.orders.Money().currencyCode("MXN").value(value)))));
        }
        return new com.paypal.orders.Order().id(id).status(status).purchaseUnits(List.of(unit));
    }

    @SuppressWarnings("unchecked")
    private PayPalHttpClient paypalClientReturning(com.paypal.orders.Order getResult) throws IOException {
        PayPalHttpClient client = mock(PayPalHttpClient.class);
        ReflectionTestUtils.setField(paymentService, "paypalClient", client);
        if (getResult != null) {
            HttpResponse<com.paypal.orders.Order> response = mock(HttpResponse.class);
            when(response.result()).thenReturn(getResult);
            lenient().when(client.execute(any(OrdersGetRequest.class))).thenReturn(response);
        }
        return client;
    }

    @SuppressWarnings("unchecked")
    private static void captureReturns(PayPalHttpClient client, com.paypal.orders.Order result) throws IOException {
        HttpResponse<com.paypal.orders.Order> response = mock(HttpResponse.class);
        when(response.result()).thenReturn(result);
        when(client.execute(any(OrdersCaptureRequest.class))).thenReturn(response);
    }

    // ─── verifyPaymentForOrder ────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyPaymentForOrder")
    class VerifyPaymentForOrder {

        @Test
        @DisplayName("does nothing for payment methods other than stripe/paypal")
        void otherMethod_noop() {
            assertThatNoException().isThrownBy(() ->
                    paymentService.verifyPaymentForOrder("COD", null, new BigDecimal("100.00")));
            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("requires a paymentId for stripe")
        void stripe_missingPaymentId() {
            assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("stripe", " ", new BigDecimal("100.00")))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("rejects a paymentId already used by another order")
        void reusedPaymentId() {
            when(orderRepository.findByPaymentId("pi_used")).thenReturn(Optional.of(new Order()));

            assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("stripe", "pi_used", new BigDecimal("100.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("otro pedido");
        }

        @Test
        @DisplayName("accepts a Stripe intent whose amount equals the order total")
        void stripe_amountMatches() throws StripeException {
            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getCurrency()).thenReturn("mxn");
            when(intent.getAmount()).thenReturn(15050L);
            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.retrieve("pi_ok")).thenReturn(intent);

                paymentService.verifyPaymentForOrder("stripe", "pi_ok", new BigDecimal("150.50"));

                verify(intent, never()).update(any(com.stripe.param.PaymentIntentUpdateParams.class));
            }
        }

        @Test
        @DisplayName("updates an unconfirmed Stripe intent to the server-computed total")
        void stripe_unconfirmedIntentUpdated() throws StripeException {
            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getCurrency()).thenReturn("mxn");
            when(intent.getAmount()).thenReturn(100L);
            when(intent.getStatus()).thenReturn("requires_payment_method");
            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.retrieve("pi_cheap")).thenReturn(intent);

                paymentService.verifyPaymentForOrder("stripe", "pi_cheap", new BigDecimal("10000.00"));

                verify(intent).update(argThat((com.stripe.param.PaymentIntentUpdateParams p) ->
                        Long.valueOf(1000000L).equals(p.getAmount())));
            }
        }

        @Test
        @DisplayName("rejects an already-paid Stripe intent for a different amount")
        void stripe_paidIntentMismatch() throws StripeException {
            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getCurrency()).thenReturn("mxn");
            when(intent.getAmount()).thenReturn(100L);
            when(intent.getStatus()).thenReturn("succeeded");
            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.retrieve("pi_paid")).thenReturn(intent);

                assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("stripe", "pi_paid", new BigDecimal("10000.00")))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("monto");
            }
        }

        @Test
        @DisplayName("rejects a Stripe intent in another currency")
        void stripe_wrongCurrency() {
            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getCurrency()).thenReturn("usd");
            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.retrieve("pi_usd")).thenReturn(intent);

                assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("stripe", "pi_usd", new BigDecimal("100.00")))
                        .isInstanceOf(BadRequestException.class);
            }
        }

        @Test
        @DisplayName("accepts a COMPLETED PayPal capture for the order total")
        void paypal_ok() throws IOException {
            mockPayPalCapture("CAP-OK", "COMPLETED", "MXN", "150.50");

            assertThatNoException().isThrownBy(() ->
                    paymentService.verifyPaymentForOrder("paypal", "CAP-OK", new BigDecimal("150.5")));
        }

        @Test
        @DisplayName("rejects a PayPal capture for a smaller amount")
        void paypal_amountMismatch() throws IOException {
            mockPayPalCapture("CAP-LOW", "COMPLETED", "MXN", "1.00");

            assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("paypal", "CAP-LOW", new BigDecimal("10000.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("monto");
        }

        @Test
        @DisplayName("accepts an APPROVED (not yet captured) PayPal order for the order total")
        void paypal_approvedOrder_ok() throws IOException {
            paypalClientReturning(paypalApiOrder("PP-1", "APPROVED", "150.50", null, null));

            assertThatNoException().isThrownBy(() ->
                    paymentService.verifyPaymentForOrder("paypal", "PP-1", new BigDecimal("150.5")));
        }

        @Test
        @DisplayName("rejects a PayPal order the buyer has not approved")
        void paypal_notApproved() throws IOException {
            paypalClientReturning(paypalApiOrder("PP-2", "CREATED", "150.50", null, null));

            assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("paypal", "PP-2", new BigDecimal("150.50")))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("aprobado");
        }

        @Test
        @DisplayName("rejects an approved PayPal order for a different amount")
        void paypal_approvedOrder_amountMismatch() throws IOException {
            paypalClientReturning(paypalApiOrder("PP-3", "APPROVED", "1.00", null, null));

            assertThatThrownBy(() -> paymentService.verifyPaymentForOrder("paypal", "PP-3", new BigDecimal("150.50")))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("monto");
        }
    }

    // ─── capturePayPalForOrder ────────────────────────────────────────────────

    @Nested
    @DisplayName("capturePayPalForOrder")
    class CapturePayPalForOrder {

        private Order pendingOrder() {
            Product product = Product.builder().id(1L).name("Shirt").stockQuantity(5).colors(new ArrayList<>()).build();
            Order order = paypalOrder("ORD-PP", "PP-ORDER", "150.50");
            order.setItems(new ArrayList<>(List.of(OrderItem.builder().product(product).quantity(2).build())));
            when(orderRepository.findByOrderNumber("ORD-PP")).thenReturn(Optional.of(order));
            return order;
        }

        @Test
        @DisplayName("captures, stores the capture id and confirms the order")
        void success() throws IOException {
            Order order = pendingOrder();
            PayPalHttpClient client = paypalClientReturning(null);
            captureReturns(client, paypalApiOrder("PP-ORDER", "COMPLETED", "150.50", "CAP-1", "COMPLETED"));

            paymentService.capturePayPalForOrder("ORD-PP");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("CAP-1");
            verify(productRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("when PayPal declines, cancels the order and restores its stock")
        void declined() throws IOException {
            Order order = pendingOrder();
            PayPalHttpClient client = paypalClientReturning(paypalApiOrder("PP-ORDER", "APPROVED", "150.50", null, null));
            when(client.execute(any(OrdersCaptureRequest.class))).thenThrow(new IOException("INSTRUMENT_DECLINED"));

            assertThatThrownBy(() -> paymentService.capturePayPalForOrder("ORD-PP"))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("cancelado");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(productRepository).incrementStock(1L, 2);
        }

        @Test
        @DisplayName("if a previous attempt already captured it, confirms instead of cancelling")
        void alreadyCapturedByPreviousAttempt() throws IOException {
            Order order = pendingOrder();
            PayPalHttpClient client = paypalClientReturning(paypalApiOrder("PP-ORDER", "COMPLETED", "150.50", "CAP-2", "COMPLETED"));
            when(client.execute(any(OrdersCaptureRequest.class))).thenThrow(new IOException("ORDER_ALREADY_CAPTURED"));

            paymentService.capturePayPalForOrder("ORD-PP");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("CAP-2");
        }

        @Test
        @DisplayName("a captured amount that differs from the total is left PENDING for manual review")
        void amountMismatch() throws IOException {
            Order order = pendingOrder();
            PayPalHttpClient client = paypalClientReturning(null);
            captureReturns(client, paypalApiOrder("PP-ORDER", "COMPLETED", "1.00", "CAP-3", "COMPLETED"));

            assertThatThrownBy(() -> paymentService.capturePayPalForOrder("ORD-PP"))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("monto");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("is a no-op for an already confirmed order, and rejects non-PayPal orders")
        void confirmedAndNonPayPal() {
            Order confirmed = paypalOrder("ORD-OK", "CAP-OK", "10.00");
            confirmed.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findByOrderNumber("ORD-OK")).thenReturn(Optional.of(confirmed));
            assertThatNoException().isThrownBy(() -> paymentService.capturePayPalForOrder("ORD-OK"));

            Order card = paypalOrder("ORD-CARD", "pi_1", "10.00");
            card.setPaymentMethod("stripe");
            when(orderRepository.findByOrderNumber("ORD-CARD")).thenReturn(Optional.of(card));
            assertThatThrownBy(() -> paymentService.capturePayPalForOrder("ORD-CARD"))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
