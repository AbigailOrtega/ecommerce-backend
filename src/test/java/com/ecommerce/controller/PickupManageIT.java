package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rescheduling and cancelling a pickup: from the emailed link (guests; the token is the
 * permission) and from the buyer's account. Real database, real pickup rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("e2e")
@DisplayName("Pickup reschedule / cancel — Integration")
class PickupManageIT {

    @Autowired TestRestTemplate rest;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @LocalServerPort int port;

    private final LocalDate firstDate = LocalDate.now().plusDays(10);
    private final LocalDate secondDate = LocalDate.now().plusDays(12);
    private String adminToken;
    private Long locationId;

    private String url(String path) { return "http://localhost:" + port + path; }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> res = rest.postForEntity(url("/api/auth/login"), new LoginRequest(email, password), ApiResponse.class);
        return (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    private HttpHeaders json(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) h.setBearerAuth(bearer);
        return h;
    }

    private HttpHeaders withToken(String token) {
        HttpHeaders h = json(null);
        if (token != null) h.set("X-Pickup-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> res) {
        return (Map<String, Object>) res.getBody().get("data");
    }

    @BeforeEach
    @SuppressWarnings("rawtypes")
    void pickupPointWithTwoDates() {
        adminToken = login("admin@test.com", "Admin123!");
        ResponseEntity<Map> loc = rest.exchange(url("/api/admin/pickup-locations"), HttpMethod.POST, new HttpEntity<>(
                Map.of("name", "Punto " + System.nanoTime(), "address", "Calle 1", "city", "CDMX", "state", "CDMX"),
                json(adminToken)), Map.class);
        assertThat(loc.getStatusCode()).isEqualTo(HttpStatus.OK);
        locationId = ((Number) data(loc).get("id")).longValue();
        for (LocalDate date : List.of(firstDate, secondDate)) {
            ResponseEntity<Map> rule = rest.exchange(url("/api/admin/pickup-locations/" + locationId + "/availability"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("type", "SPECIFIC_DATE", "specificDate", date.toString(),
                            "startTime", "10:00", "endTime", "14:00", "maxCapacity", 5), json(adminToken)), Map.class);
            assertThat(rule.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @SuppressWarnings("rawtypes")
    private String guestPickupOrder() {
        Long productId = productRepository.findBySlug("test-t-shirt").orElseThrow().getId();
        ResponseEntity<Map> res = rest.exchange(url("/api/orders/guest"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "guestFirstName", "Lucía", "guestLastName", "Méndez", "guestEmail", "lucia" + System.nanoTime() + "@example.com",
                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                "paymentMethod", "COD", "shippingType", "PICKUP",
                "pickupLocationId", locationId, "pickupDate", firstDate.toString()), json(null)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.CREATED);
        return (String) data(res).get("orderNumber");
    }

    private String tokenOf(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow().getManageToken();
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("guest link: see the pickup, reschedule it, cancel it, then pick a date again")
    void guestLinkLifecycle() {
        String order = guestPickupOrder();
        String token = tokenOf(order);
        assertThat(token).hasSize(36);

        ResponseEntity<Map> view = rest.exchange(url("/api/orders/" + order + "/pickup"), HttpMethod.GET, new HttpEntity<>(withToken(token)), Map.class);
        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(view)).containsEntry("pickupDate", firstDate.toString())
                .containsEntry("pickupLocationId", locationId.intValue())
                .doesNotContainKeys("guestEmail", "guestPhone", "manageToken", "paymentId", "notes");

        ResponseEntity<Map> moved = rest.exchange(url("/api/orders/" + order + "/pickup/reschedule"), HttpMethod.POST,
                new HttpEntity<>(Map.of("pickupDate", secondDate.toString()), withToken(token)), Map.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(moved)).containsEntry("pickupDate", secondDate.toString()).containsEntry("pickupCancelled", false);

        ResponseEntity<Map> cancelled = rest.exchange(url("/api/orders/" + order + "/pickup/cancel"), HttpMethod.POST,
                new HttpEntity<>(withToken(token)), Map.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(cancelled)).containsEntry("pickupCancelled", true);
        assertThat(rest.exchange(url("/api/orders/" + order + "/pickup/cancel"), HttpMethod.POST,
                new HttpEntity<>(withToken(token)), Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> again = rest.exchange(url("/api/orders/" + order + "/pickup/reschedule"), HttpMethod.POST,
                new HttpEntity<>(Map.of("pickupDate", firstDate.toString()), withToken(token)), Map.class);
        assertThat(data(again)).containsEntry("pickupDate", firstDate.toString()).containsEntry("pickupCancelled", false);
    }

    @Test
    @DisplayName("without the right token: 404, as for an unknown order (the order number is not enough)")
    void wrongToken() {
        String order = guestPickupOrder();
        for (String token : new String[]{null, "", "not-the-token", tokenOf(order).replace('a', 'b').replace('1', '2')}) {
            assertThat(rest.exchange(url("/api/orders/" + order + "/pickup"), HttpMethod.GET,
                    new HttpEntity<>(withToken(token)), String.class).getStatusCode()).as(String.valueOf(token)).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(rest.exchange(url("/api/orders/" + order + "/pickup/cancel"), HttpMethod.POST,
                    new HttpEntity<>(withToken(token)), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(orderRepository.findByOrderNumber(order).orElseThrow().isPickupCancelled()).isFalse();
    }

    @Test
    @DisplayName("the token never appears in API responses (public tracking, admin)")
    void tokenNeverSerialized() {
        String order = guestPickupOrder();
        String token = tokenOf(order);
        assertThat(rest.getForEntity(url("/api/orders/track/" + order), String.class).getBody()).doesNotContain(token);
        assertThat(rest.exchange(url("/api/admin/orders?search=" + order), HttpMethod.GET,
                new HttpEntity<>(json(adminToken)), String.class).getBody()).contains(order).doesNotContain(token);
    }

    @Test
    @DisplayName("a delivered order's pickup can no longer be changed")
    void deliveredIsFinal() {
        String order = guestPickupOrder();
        Order o = orderRepository.findByOrderNumber(order).orElseThrow();
        o.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(o);
        ResponseEntity<String> res = rest.exchange(url("/api/orders/" + order + "/pickup/cancel"), HttpMethod.POST,
                new HttpEntity<>(withToken(tokenOf(order))), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("entregado");
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("from the account: the buyer reschedules and cancels; someone else cannot")
    void fromTheAccount() {
        String email = "buyer" + System.nanoTime() + "@example.com";
        rest.postForEntity(url("/api/auth/register"), Map.of("firstName", "Ana", "lastName", "López", "email", email,
                "password", "Buyer123!", "marketingOptIn", false), String.class);
        String buyer = login(email, "Buyer123!");
        Long productId = productRepository.findBySlug("test-t-shirt").orElseThrow().getId();
        rest.exchange(url("/api/cart"), HttpMethod.POST, new HttpEntity<>(Map.of("productId", productId, "quantity", 1), json(buyer)), String.class);
        ResponseEntity<Map> created = rest.exchange(url("/api/orders"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "paymentMethod", "COD", "shippingType", "PICKUP", "pickupLocationId", locationId,
                "pickupDate", firstDate.toString()), json(buyer)), Map.class);
        assertThat(created.getStatusCode()).as(String.valueOf(created.getBody())).isEqualTo(HttpStatus.CREATED);
        String order = (String) data(created).get("orderNumber");

        String stranger = login("test@test.com", "Test123!");
        assertThat(rest.exchange(url("/api/orders/" + order + "/cancel-pickup"), HttpMethod.PATCH,
                new HttpEntity<>(json(stranger)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> moved = rest.exchange(url("/api/orders/" + order + "/reschedule-pickup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("pickupDate", secondDate.toString()), json(buyer)), Map.class);
        assertThat(data(moved)).containsEntry("pickupDate", secondDate.toString());
        ResponseEntity<Map> cancelled = rest.exchange(url("/api/orders/" + order + "/cancel-pickup"), HttpMethod.PATCH,
                new HttpEntity<>(json(buyer)), Map.class);
        assertThat(data(cancelled)).containsEntry("pickupCancelled", true);
    }
}
