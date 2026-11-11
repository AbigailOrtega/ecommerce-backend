package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentFeeJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The accounting report over the real database: sales count on the day they are paid (never while
 * pending), refunds on their own day, garment costs recorded per sale, commissions filled in by
 * the job, and the cost never shown to the public.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.payments.fees.enabled=true")
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Accounting report — Integration")
class AccountingReportIT {

    @Autowired TestRestTemplate rest;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentFeeJob paymentFeeJob;
    @Autowired com.ecommerce.service.PaymentService paymentService;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @LocalServerPort int port;

    private String admin;
    private final String today = LocalDate.now().toString();

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders json(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) h.setBearerAuth(bearer);
        return h;
    }

    @SuppressWarnings("unchecked")
    private static <T> T data(ResponseEntity<Map> res) {
        return (T) res.getBody().get("data");
    }

    @BeforeEach
    void login() {
        ResponseEntity<ApiResponse> res = rest.postForEntity(url("/api/auth/login"), new LoginRequest("admin@test.com", "Admin123!"), ApiResponse.class);
        admin = (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private Long product(String price) {
        ResponseEntity<Map> res = rest.exchange(url("/api/products"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "name", "Prenda contable " + System.nanoTime(), "price", new BigDecimal(price), "stockQuantity", 20,
                "active", true), json(admin)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return ((Number) ((Map) data(res)).get("id")).longValue();
    }

    @SuppressWarnings("rawtypes")
    private Order guestOrder(Long productId, int qty) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "guestFirstName", "Rosa", "guestLastName", "Díaz", "guestEmail", "rosa" + System.nanoTime() + "@example.com",
                "items", List.of(Map.of("productId", productId, "quantity", qty)), "paymentMethod", "COD",
                "shippingType", "NATIONAL", "shippingAddress", "Calle 5 #10", "shippingCity", "Puebla"));
        body.putAll(Map.of("shippingState", "Puebla", "shippingZipCode", "72000", "shippingCountry", "MX"));
        ResponseEntity<Map> res = rest.exchange(url("/api/orders/guest"), HttpMethod.POST, new HttpEntity<>(body, json(null)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.CREATED);
        return orderRepository.findByOrderNumber((String) ((Map) data(res)).get("orderNumber")).orElseThrow();
    }

    /** Unit cost of the order's first item (read in a transaction: items load lazily). */
    private BigDecimal unitCost(String orderNumber) {
        return tx.execute(st -> orderRepository.findByOrderNumber(orderNumber).orElseThrow().getItems().get(0).getUnitCost());
    }

    private void setStatus(Order o, String status) {
        assertThat(rest.exchange(url("/api/admin/orders/" + o.getId() + "/status"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", status), json(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> report() {
        ResponseEntity<Map> res = rest.exchange(url("/api/admin/reports/accounting?from=" + today + "&to=" + today),
                HttpMethod.GET, new HttpEntity<>(json(admin)), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) res.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(Map<String, Object> report, String orderNumber) {
        return ((List<Map<String, Object>>) report.get("ledger")).stream()
                .filter(r -> orderNumber.equals(r.get("orderNumber"))).toList();
    }

    @Test
    @DisplayName("pending is not a sale; paid counts on its day with its garment cost; a refund is its own movement")
    void lifecycle() {
        Long productId = product("250.00");
        assertThat(rest.exchange(url("/api/admin/reports/product-costs/" + productId), HttpMethod.PUT,
                new HttpEntity<>(Map.of("costPrice", 90), json(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        Order order = guestOrder(productId, 2);
        assertThat(unitCost(order.getOrderNumber())).isEqualByComparingTo("90");
        assertThat(rowsOf(report(), order.getOrderNumber())).as("pending: not in the accounts").isEmpty();

        setStatus(order, "CONFIRMED");
        List<Map<String, Object>> rows = rowsOf(report(), order.getOrderNumber());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("movement", "VENTA").containsEntry("customer", "Rosa Díaz")
                .containsEntry("paymentMethod", "cod");
        assertThat(new BigDecimal(rows.get(0).get("productSales").toString())).isEqualByComparingTo("500");
        assertThat(new BigDecimal(rows.get(0).get("costOfGoods").toString())).isEqualByComparingTo("180");

        setStatus(order, "REFUNDED");
        rows = rowsOf(report(), order.getOrderNumber());
        assertThat(rows).extracting(r -> r.get("movement")).containsExactly("VENTA", "REEMBOLSO");
        assertThat(new BigDecimal(rows.get(1).get("total").toString()))
                .isEqualByComparingTo(order.getTotalAmount().negate());
        assertThat(new BigDecimal(rows.get(1).get("costOfGoods").toString())).isEqualByComparingTo("-180");
    }

    @Test
    @DisplayName("a cost entered later fills the past sales that had none; the cost is never public")
    void costLaterAndPrivate() {
        Long productId = product("300.00");
        Order before = guestOrder(productId, 1);
        assertThat(unitCost(before.getOrderNumber())).isNull();

        ResponseEntity<String> set = rest.exchange(url("/api/admin/reports/product-costs/" + productId), HttpMethod.PUT,
                new HttpEntity<>(Map.of("costPrice", 120.5), json(admin)), String.class);
        assertThat(set.getBody()).contains("1 venta anterior");
        assertThat(unitCost(before.getOrderNumber())).isEqualByComparingTo("120.50");

        assertThat(rest.getForEntity(url("/api/products/" + productId), String.class).getBody())
                .doesNotContain("costPrice").doesNotContain("120.5");
        assertThat(rest.getForEntity(url("/api/admin/reports/product-costs"), String.class).getStatusCode())
                .as("anonymous").isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        Map<String, Object> negative = new HashMap<>();
        negative.put("costPrice", -1);
        assertThat(rest.exchange(url("/api/admin/reports/product-costs/" + productId), HttpMethod.PUT,
                new HttpEntity<>(negative, json(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("the job fills in the card commission; a payment the provider does not know is retried a few times, then left")
    void commissions() {
        Long productId = product("100.00");
        Order card = guestOrder(productId, 1);
        Order lost = guestOrder(productId, 1);
        setStatus(card, "CONFIRMED");
        setStatus(lost, "CONFIRMED");
        // as if paid by card: the (fake) provider knows the first payment, not the second
        card = orderRepository.findByOrderNumber(card.getOrderNumber()).orElseThrow();
        card.setPaymentMethod("stripe");
        card.setPaymentId(paymentService.createPaymentIntent(card.getTotalAmount(), "mxn").get("paymentIntentId"));
        orderRepository.save(card);
        lost = orderRepository.findByOrderNumber(lost.getOrderNumber()).orElseThrow();
        lost.setPaymentMethod("stripe");
        lost.setPaymentId("pi_unknown_" + System.nanoTime());
        orderRepository.save(lost);

        for (int i = 0; i < 6; i++) paymentFeeJob.run();

        Order cardAfter = orderRepository.findByOrderNumber(card.getOrderNumber()).orElseThrow();
        // fake Stripe: 3.6% + $3
        assertThat(cardAfter.getPaymentFee()).isEqualByComparingTo(
                cardAfter.getTotalAmount().multiply(new BigDecimal("0.036")).add(new BigDecimal("3")).setScale(2, java.math.RoundingMode.HALF_UP));
        Order lostAfter = orderRepository.findByOrderNumber(lost.getOrderNumber()).orElseThrow();
        assertThat(lostAfter.getPaymentFee()).isNull();
        assertThat(lostAfter.getPaymentFeeAttempts()).isEqualTo(5);

        Map<String, Object> row = rowsOf(report(), card.getOrderNumber()).get(0);
        assertThat(new BigDecimal(row.get("paymentFee").toString())).isEqualByComparingTo(cardAfter.getPaymentFee());
    }

    @Test
    @DisplayName("rejects a backwards range")
    void badRange() {
        assertThat(rest.exchange(url("/api/admin/reports/accounting?from=2026-10-31&to=2026-10-01"), HttpMethod.GET,
                new HttpEntity<>(json(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
