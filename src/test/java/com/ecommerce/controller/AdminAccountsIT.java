package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.User;
import com.ecommerce.repository.UserRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin accounts end to end on the embedded database: invite → set password → panel → revoke. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("e2e")
@DisplayName("Admin accounts — Integration")
class AdminAccountsIT {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @LocalServerPort int port;

    private String base(String path) {
        return "http://localhost:" + port + path;
    }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> res = restTemplate.postForEntity(base("/api/auth/login"),
                new LoginRequest(email, password), ApiResponse.class);
        assertThat(res.getStatusCode()).as("login " + email).isEqualTo(HttpStatus.OK);
        return (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(base(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime() + "@shop.test";
    }

    @Test
    @DisplayName("an invited person sets a password with the emailed link, uses the panel, and loses it when revoked")
    void inviteToRevoke() {
        String admin = login("admin@test.com", "Admin123!");
        String email = unique("invited");

        ResponseEntity<String> invited = call(HttpMethod.POST, "/api/admin/users/admins", admin,
                Map.of("email", email, "firstName", "Sofía", "lastName", "Ruiz"));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invited.getBody()).contains("\"role\":\"ADMIN\"");

        // the link in the invitation email
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getResetToken()).isNotBlank();
        ResponseEntity<String> setPassword = restTemplate.postForEntity(base("/api/auth/reset-password"),
                Map.of("token", created.getResetToken(), "newPassword", "Sofia123!"), String.class);
        assertThat(setPassword.getStatusCode()).isEqualTo(HttpStatus.OK);

        String newAdmin = login(email, "Sofia123!");
        assertThat(call(HttpMethod.GET, "/api/admin/users", newAdmin, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // nobody can take their own access away
        ResponseEntity<String> self = call(HttpMethod.DELETE, "/api/admin/users/" + created.getId() + "/admin", newAdmin, null);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> revoked = call(HttpMethod.DELETE, "/api/admin/users/" + created.getId() + "/admin", admin, null);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revoked.getBody()).contains("\"role\":\"CUSTOMER\"");

        // roles are read on every request: the token issued while admin no longer opens the panel
        assertThat(call(HttpMethod.GET, "/api/admin/users", newAdmin, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an existing customer is promoted in place and keeps their password")
    void promotesCustomer() {
        String admin = login("admin@test.com", "Admin123!");
        String email = unique("customer");
        ResponseEntity<String> registered = restTemplate.postForEntity(base("/api/auth/register"),
                Map.of("firstName", "Leo", "lastName", "Díaz", "email", email, "password", "Leo12345!", "marketingOptIn", false),
                String.class);
        assertThat(registered.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(call(HttpMethod.POST, "/api/admin/users/admins", admin, Map.of("email", email.toUpperCase()))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        String promoted = login(email, "Leo12345!");
        assertThat(call(HttpMethod.GET, "/api/admin/users", promoted, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // a second invitation for the same person is refused
        assertThat(call(HttpMethod.POST, "/api/admin/users/admins", admin, Map.of("email", email)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("customers cannot give or take admin access")
    void customersForbidden() {
        String customer = login("test@test.com", "Test123!");
        assertThat(call(HttpMethod.POST, "/api/admin/users/admins", customer, Map.of("email", unique("x")))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Long adminId = userRepository.findByEmail("admin@test.com").orElseThrow().getId();
        assertThat(call(HttpMethod.DELETE, "/api/admin/users/" + adminId + "/admin", customer, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an invalid email → 400")
    void invalidEmail() {
        String admin = login("admin@test.com", "Admin123!");
        assertThat(call(HttpMethod.POST, "/api/admin/users/admins", admin, Map.of("email", "not-an-email"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
