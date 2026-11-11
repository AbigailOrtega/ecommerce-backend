package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin → Productos search over the real database: name or SKU, price range, active or not. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Admin product search — Integration")
class AdminProductSearchIT {

    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    private String admin;
    private final String tag = "Bq" + System.nanoTime();

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(admin);
        return h;
    }

    @BeforeEach
    void login() {
        ResponseEntity<ApiResponse> res = rest.postForEntity(url("/api/auth/login"), new LoginRequest("admin@test.com", "Admin123!"), ApiResponse.class);
        admin = (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private void product(String name, String sku, String price, boolean active) {
        ResponseEntity<Map> res = rest.exchange(url("/api/products"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "name", name, "sku", sku, "price", new BigDecimal(price), "stockQuantity", 3, "active", active), json()), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> names(String query) {
        ResponseEntity<Map> res = rest.exchange(url("/api/admin/products?size=100&" + query), HttpMethod.GET, new HttpEntity<>(json()), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) ((Map) res.getBody().get("data")).get("content");
        return content.stream().map(p -> (String) p.get("name")).filter(n -> n.contains(tag)).sorted().toList();
    }

    @Test
    @DisplayName("filters by name or SKU, price range and active, alone or together")
    void filters() {
        product("Vestido " + tag, "VES-" + tag + "-1", "139", true);
        product("Sweater " + tag, "SWE-" + tag + "-2", "100", false);
        product("Falda " + tag, "FAL-" + tag + "-3", "199", true);

        assertThat(names("q=" + tag.toLowerCase())).hasSize(3);                         // any case, part of the name
        assertThat(names("q=swe-" + tag)).containsExactly("Sweater " + tag);              // SKU
        assertThat(names("q=" + tag + "&minPrice=120&maxPrice=199")).containsExactly("Falda " + tag, "Vestido " + tag);
        assertThat(names("q=" + tag + "&active=false")).containsExactly("Sweater " + tag);
        assertThat(names("q=" + tag + "&active=true&maxPrice=150")).containsExactly("Vestido " + tag);
        assertThat(names("q=" + tag + "%25")).isEmpty();                                  // % is a character, not a wildcard
    }

    @Test
    @DisplayName("a minimum price above the maximum is refused")
    @SuppressWarnings("rawtypes")
    void invertedRange() {
        ResponseEntity<Map> res = rest.exchange(url("/api/admin/products?minPrice=200&maxPrice=100"), HttpMethod.GET, new HttpEntity<>(json()), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
