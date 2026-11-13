package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.tenant.Tenant;
import com.ecommerce.tenant.TenantContext;
import com.ecommerce.tenant.TenantDomain;
import com.ecommerce.tenant.TenantDomainRepository;
import com.ecommerce.tenant.TenantRepository;
import com.ecommerce.tenant.TenantResolver;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two stores on one backend: each request belongs to the store of the host it came through
 * (X-Store-Host, sent by the frontend), and nothing of one store can be seen or changed from another.
 * Marea is store #1, served for any host no store claims (localhost here).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Multi-store isolation — Integration")
class TenantIsolationIT {

    @Autowired TestRestTemplate rest;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantDomainRepository domainRepository;
    @Autowired TenantResolver tenantResolver;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @LocalServerPort int port;

    private final String tag = "t" + System.nanoTime();
    /** The second store's domain; null = Marea (no forwarded host). */
    private final String papeleria = "papeleria-" + tag + ".test";
    private Tenant papeleriaStore;

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders headers(String host, String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (host != null) h.set("X-Store-Host", host);
        if (bearer != null) h.setBearerAuth(bearer);
        return h;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> call(HttpMethod method, String path, String host, String bearer, Object body) {
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers(host, bearer)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T data(ResponseEntity<Map> res) {
        return (T) res.getBody().get("data");
    }

    private String login(String host, String email, String password) {
        ResponseEntity<Map> res = call(HttpMethod.POST, "/api/auth/login", host, null, new LoginRequest(email, password));
        assertThat(res.getStatusCode()).as("login " + email + " @ " + host + ": " + res.getBody()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<?, ?>) data(res)).get("accessToken");
    }

    @BeforeEach
    void secondStore() {
        papeleriaStore = tenantRepository.save(Tenant.builder().slug(tag).name("Papelería " + tag).build());
        domainRepository.save(TenantDomain.builder().domain(papeleria).tenant(papeleriaStore).build());
        TenantContext.runAs(papeleriaStore.getId(), () -> userRepository.save(User.builder()
                .firstName("Pepa").lastName("Pérez").email("admin@test.com")
                .password(passwordEncoder.encode("Papeleria123!")).role(Role.ADMIN).build()));
        tenantResolver.evictAll();
    }

    @SuppressWarnings("rawtypes")
    private Long product(String host, String bearer, String name) {
        ResponseEntity<Map> res = call(HttpMethod.POST, "/api/products", host, bearer, Map.of(
                "name", name, "price", new BigDecimal("50"), "stockQuantity", 10, "active", true, "sku", "SKU-" + tag));
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return ((Number) ((Map) data(res)).get("id")).longValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> catalog(String host) {
        ResponseEntity<Map> res = call(HttpMethod.GET, "/api/products?size=1000", host, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object d = data(res);
        List<Map<String, Object>> rows = d instanceof Map m ? (List<Map<String, Object>>) m.get("content") : (List<Map<String, Object>>) d;
        return rows.stream().map(r -> (String) r.get("name")).toList();
    }

    @Test
    @DisplayName("each store's catalog shows only its own products; the same SKU can exist in both")
    void catalogs() {
        String mareaAdmin = login(null, "admin@test.com", "Admin123!");
        String papeleriaAdmin = login(papeleria, "admin@test.com", "Papeleria123!");

        product(null, mareaAdmin, "Falda " + tag);
        Long cuaderno = product(papeleria, papeleriaAdmin, "Cuaderno " + tag);

        assertThat(catalog(papeleria)).contains("Cuaderno " + tag).doesNotContain("Falda " + tag);
        assertThat(catalog(null)).contains("Falda " + tag).doesNotContain("Cuaderno " + tag);

        // asking Marea for the stationery shop's product by id finds nothing
        assertThat(call(HttpMethod.GET, "/api/products/" + cuaderno, null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/products/" + cuaderno, papeleria, null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an admin session from one store opens nothing in another, even with the same e-mail")
    void sessionsStayInTheirStore() {
        String papeleriaAdmin = login(papeleria, "admin@test.com", "Papeleria123!");
        Long cuaderno = product(papeleria, papeleriaAdmin, "Lápiz " + tag);

        // the stationery admin's token, sent to Marea: anonymous there
        assertThat(call(HttpMethod.GET, "/api/admin/products", null, papeleriaAdmin, null).getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        // Marea's admin cannot delete the stationery shop's product: in Marea it does not exist
        String mareaAdmin = login(null, "admin@test.com", "Admin123!");
        assertThat(call(HttpMethod.DELETE, "/api/products/" + cuaderno, null, mareaAdmin, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/products/" + cuaderno, papeleria, null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // and the stationery password does not open Marea's admin account
        assertThat(call(HttpMethod.POST, "/api/auth/login", null, null, new LoginRequest("admin@test.com", "Papeleria123!"))
                .getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a customer account belongs to the store it was created in")
    void customersPerStore() {
        String email = "cliente-" + tag + "@example.com";
        Map<String, Object> signup = Map.of("firstName", "Ana", "lastName", "Ruiz", "email", email, "password", "Cliente123!");
        assertThat(call(HttpMethod.POST, "/api/auth/register", papeleria, null, signup).getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        // unknown in Marea, so the same e-mail can open a separate account there
        assertThat(call(HttpMethod.POST, "/api/auth/login", null, null, new LoginRequest(email, "Cliente123!")).getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/auth/register", null, null, signup).getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @Test
    @DisplayName("without X-Store-Host the forwarded host still picks the store")
    void forwardedHost() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Forwarded-Host", "www." + papeleria);
        String login = "{\"email\":\"admin@test.com\",\"password\":\"Papeleria123!\"}";
        h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(rest.exchange(url("/api/auth/login"), HttpMethod.POST, new HttpEntity<>(login, h), Map.class).getStatusCode())
                .as("www. is the same store").isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a suspended store answers 404 instead of falling back to Marea")
    void suspended() {
        papeleriaStore.setStatus(Tenant.Status.SUSPENDED);
        tenantRepository.save(papeleriaStore);
        tenantResolver.evictAll();

        ResponseEntity<Map> res = call(HttpMethod.GET, "/api/products", papeleria, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(res.getBody())).contains("no está disponible");
        assertThat(res.getBody()).containsEntry("code", "STORE_SUSPENDED"); // the frontend shows its "not available" page on it
    }
}
