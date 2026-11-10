package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Postal code lookup on the embedded database, with the codes TestDataInitializer seeds. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("e2e")
@DisplayName("Postal codes — Integration")
class PostalCodeControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    private String base(String path) {
        return "http://localhost:" + port + path;
    }

    private String token(String email, String password) {
        ResponseEntity<ApiResponse> res = restTemplate.postForEntity(base("/api/auth/login"), new LoginRequest(email, password), ApiResponse.class);
        return (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    @Test
    @DisplayName("a known code: state, municipality, city and its colonias — public and cacheable")
    void lookup() {
        ResponseEntity<String> res = restTemplate.getForEntity(base("/api/shipping/postal-codes/44100"), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"state\":\"Jalisco\"", "\"municipality\":\"Guadalajara\"",
                "Guadalajara Centro", "Zona Centro");
        assertThat(res.getHeaders().getCacheControl()).contains("public").contains("max-age=86400");
    }

    @Test
    @DisplayName("unknown and malformed codes → 404")
    void notFound() {
        assertThat(restTemplate.getForEntity(base("/api/shipping/postal-codes/99999"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity(base("/api/shipping/postal-codes/abc"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("catalog status is for admins only")
    void statusAdminOnly() {
        HttpHeaders admin = new HttpHeaders();
        admin.setBearerAuth(token("admin@test.com", "Admin123!"));
        ResponseEntity<String> ok = restTemplate.exchange(base("/api/admin/postal-codes/status"), HttpMethod.GET, new HttpEntity<>(admin), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("\"rows\":");

        HttpHeaders customer = new HttpHeaders();
        customer.setBearerAuth(token("test@test.com", "Test123!"));
        assertThat(restTemplate.exchange(base("/api/admin/postal-codes/refresh"), HttpMethod.POST, new HttpEntity<>(customer), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
