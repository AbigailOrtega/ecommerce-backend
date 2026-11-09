package com.ecommerce.service;

import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * End to end through the real webhook endpoint: a signed Stripe "payment_intent.succeeded" confirms
 * the order and the shopper gets the "pedido confirmado" email, sent after the transaction commits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("e2e")
@DisplayName("Order paid → confirmation email")
class OrderPaidEmailIT {

    private static final String WEBHOOK_SECRET = "whsec_test"; // application-e2e.yml

    @Autowired TestRestTemplate restTemplate;
    @Autowired OrderRepository orderRepository;
    @LocalServerPort int port;
    @MockitoBean EmailService emailService;

    @Test
    @DisplayName("a guest's order confirmed by Stripe's webhook emails the guest the CONFIRMED status")
    void stripeWebhookConfirmsAndEmails() throws Exception {
        orderRepository.save(Order.builder()
                .orderNumber("ORD-PAID-EMAIL").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("150.00")).paymentMethod("stripe").paymentId("pi_paid_email")
                .guestEmail("ana@example.com").guestFirstName("Ana")
                .shippingAddress("Calle 1").shippingCity("CDMX").shippingState("CDMX")
                .shippingZipCode("01000").shippingCountry("MX")
                .build());

        String payload = """
                {"id":"evt_paid_email","object":"event","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"pi_paid_email","object":"payment_intent","amount_received":15000,"currency":"mxn"}}}""";
        ResponseEntity<String> res = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/webhooks/stripe", signed(payload), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(orderRepository.findByOrderNumber("ORD-PAID-EMAIL").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        ArgumentCaptor<OrderResponse> sent = ArgumentCaptor.forClass(OrderResponse.class);
        verify(emailService, timeout(5000)).sendGuestOrderStatusUpdateEmail(eq("ana@example.com"), eq("Ana"), sent.capture());
        assertThat(sent.getValue().getStatus()).isEqualTo("CONFIRMED");
        assertThat(sent.getValue().getOrderNumber()).isEqualTo("ORD-PAID-EMAIL");
    }

    private HttpEntity<String> signed(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=" + timestamp + ",v1=" + signature);
        return new HttpEntity<>(payload, headers);
    }
}
