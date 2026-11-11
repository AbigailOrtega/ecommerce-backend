package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.repository.OrderRepository;
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
 * Categories over the real database: a hidden one leaves the store's list but not the admin's, and
 * its products keep it (flagged); the per-category report counts paid sales only, a product in two
 * categories in both.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Categories: visibility and sales report — Integration")
class CategoryIT {

    @Autowired TestRestTemplate rest;
    @Autowired OrderRepository orderRepository;
    @LocalServerPort int port;

    private String admin;
    private final String tag = "Ct" + System.nanoTime();
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
        assertThat(res.getStatusCode()).as("login: " + res.getBody()).isEqualTo(HttpStatus.OK);
        admin = (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private Long category(String name) {
        ResponseEntity<Map> res = rest.exchange(url("/api/categories"), HttpMethod.POST, new HttpEntity<>(Map.of("name", name), json(admin)), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) ((Map) data(res)).get("id")).longValue();
    }

    @SuppressWarnings("rawtypes")
    private Long product(String name, String price, Long... categoryIds) {
        ResponseEntity<Map> res = rest.exchange(url("/api/products"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "name", name, "price", new BigDecimal(price), "stockQuantity", 20, "active", true,
                "categoryIds", List.of(categoryIds)), json(admin)), Map.class);
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

    private void setStatus(Order o, String status) {
        assertThat(rest.exchange(url("/api/admin/orders/" + o.getId() + "/status"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", status), json(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> list(String path, String bearer) {
        ResponseEntity<Map> res = rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(json(bearer)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) res.getBody().get("data");
    }

    private static List<String> names(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> (String) r.get("name")).toList();
    }

    @Test
    @DisplayName("a hidden category leaves the store's list, not the admin's; its products keep it, flagged")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void hiding() {
        Long id = category("Ocultable " + tag);
        Long productId = product("Prenda " + tag, "100", id);

        ResponseEntity<Map> res = rest.exchange(url("/api/categories/" + id + "/visibility"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("visible", false), json(admin)), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("message")).isEqualTo("La categoría se ocultó de la tienda");

        assertThat(names(list("/api/categories", null))).doesNotContain("Ocultable " + tag);
        assertThat(list("/api/admin/categories", admin)).anySatisfy(c -> {
            assertThat(c.get("name")).isEqualTo("Ocultable " + tag);
            assertThat(c.get("visible")).isEqualTo(false);
        });
        Map<String, Object> product = (Map<String, Object>) rest.exchange(url("/api/products/" + productId), HttpMethod.GET,
                new HttpEntity<>(json(null)), Map.class).getBody().get("data");
        assertThat((List<Map<String, Object>>) product.get("categories")).singleElement()
                .satisfies(c -> assertThat(c.get("visible")).isEqualTo(false));

        // only the admin can change it
        assertThat(rest.exchange(url("/api/categories/" + id + "/visibility"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("visible", true), json(null)), Map.class).getStatusCode().value()).isIn(401, 403);

        rest.exchange(url("/api/categories/" + id + "/visibility"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("visible", true), json(admin)), Map.class);
        assertThat(names(list("/api/categories", null))).contains("Ocultable " + tag);
    }

    @Test
    @DisplayName("sales per category: paid only, a product in two categories counts in both, unsold categories show zero")
    void report() {
        Long camisas = category("Camisas " + tag);
        Long ofertas = category("Ofertas " + tag);
        Long vacia = category("Vacía " + tag);
        Long camisa = product("Camisa " + tag, "200", camisas, ofertas);
        Long otra = product("Camisa lisa " + tag, "150", camisas);

        Order paid = guestOrder(camisa, 2);          // 2 × 200
        setStatus(paid, "CONFIRMED");
        Order paid2 = guestOrder(otra, 1);           // 1 × 150
        setStatus(paid2, "CONFIRMED");
        guestOrder(camisa, 5);                       // never paid: does not count

        List<Map<String, Object>> rows = list("/api/admin/reports/categories?from=" + today + "&to=" + today, admin);
        Map<String, Map<String, Object>> byName = new HashMap<>();
        rows.forEach(r -> byName.put((String) r.get("name"), r));

        assertThat(byName.get("Camisas " + tag)).containsEntry("unitsSold", 3).containsEntry("orders", 2);
        assertThat(new BigDecimal(String.valueOf(byName.get("Camisas " + tag).get("revenue")))).isEqualByComparingTo("550");
        assertThat(byName.get("Ofertas " + tag)).containsEntry("unitsSold", 2).containsEntry("orders", 1);
        assertThat(byName.get("Vacía " + tag)).containsEntry("unitsSold", 0);
        // a day with nothing paid
        assertThat(list("/api/admin/reports/categories?from=2020-01-01&to=2020-01-02", admin))
                .allSatisfy(r -> assertThat(r.get("unitsSold")).isEqualTo(0));
    }
}
