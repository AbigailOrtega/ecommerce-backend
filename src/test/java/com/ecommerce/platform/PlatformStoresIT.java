package com.ecommerce.platform;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.tenant.TenantContext;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The super admin creates a store over the API; it answers on its domain with its own name and admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Platform: creating stores — Integration")
class PlatformStoresIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired PlatformAdminRepository platformAdmins;
    @LocalServerPort int port;

    private final String tag = "p" + System.nanoTime();

    private String url(String path) { return "http://localhost:" + port + path; }

    /** The platform API only answers on the platform's domain (application-e2e.yml). */
    private static final String PLATFORM_HOST = "plataforma.localhost";

    /** A super admin's session (super@test.com, seeded by TestDataInitializer). */
    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> platformLogin(String email, String password, String host) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Store-Host", host);
        return rest.exchange(url("/api/platform/auth/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), h), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private String platformToken(String email, String password) {
        ResponseEntity<Map> res = platformLogin(email, password, PLATFORM_HOST);
        assertThat(res.getStatusCode()).as("platform login " + email).isEqualTo(HttpStatus.OK);
        return (String) ((Map) res.getBody().get("data")).get("token");
    }

    private String superAdmin() { return platformToken("super@test.com", "Super123!"); }

    /** A store's own login (store #1, Marea). */
    @SuppressWarnings("rawtypes")
    private String storeLogin(String email, String password) {
        ResponseEntity<Map> res = rest.postForEntity(url("/api/auth/login"), Map.of("email", email, "password", password), Map.class);
        assertThat(res.getStatusCode()).as("login " + email).isEqualTo(HttpStatus.OK);
        return (String) ((Map) res.getBody().get("data")).get("accessToken");
    }

    private HttpHeaders platform(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        h.set("X-Store-Host", PLATFORM_HOST);
        return h;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> create(String token, Map<String, Object> body) {
        HttpHeaders h = platform(token);
        return rest.exchange(url("/api/platform/stores"), HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private Map<String, Object> store(String domain) {
        return Map.of("slug", tag, "name", "Papelería Luna", "domains", List.of("https://www." + domain + "/"),
                "baseUrl", "https://www." + domain, "adminEmail", "duena@" + domain, "adminFirstName", "Luna");
    }

    @Test
    @DisplayName("creates the store with its domain, name and an invited admin")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createsStore() {
        String domain = tag + ".test";
        ResponseEntity<Map> res = create(superAdmin(), store(domain));
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertThat((List<String>) data.get("domains")).containsExactly(domain);
        long storeId = ((Number) data.get("id")).longValue();

        // its own name on its domain; Marea's unchanged
        HttpHeaders h = new HttpHeaders();
        h.set("X-Store-Host", "www." + domain);
        ResponseEntity<Map> info = rest.exchange(url("/api/store-info"), HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(((Map) info.getBody().get("data")).get("name")).isEqualTo("Papelería Luna");
        ResponseEntity<Map> marea = rest.getForEntity(url("/api/store-info"), Map.class);
        assertThat(((Map) marea.getBody().get("data")).get("name")).isNotEqualTo("Papelería Luna");

        // the admin belongs to the new store, with a pending invitation, and not to Marea
        Optional<User> admin = TenantContext.callAs(storeId, () -> userRepository.findByEmail("duena@" + domain));
        assertThat(admin).hasValueSatisfying(u -> {
            assertThat(u.getRole()).isEqualTo(Role.ADMIN);
            assertThat(u.getResetToken()).isNotBlank();
        });
        assertThat(userRepository.findByEmail("duena@" + domain)).isEmpty();

        // the same domain cannot be given to a second store
        Map<String, Object> again = new java.util.HashMap<>(store(domain));
        again.put("slug", tag + "-2");
        assertThat(create(superAdmin(), again).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resending the invitation gives the store's admin a fresh link; a domain can be added")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void resendAndDomain() {
        String domain = tag + "-r.test";
        Map<String, Object> body = new java.util.HashMap<>(store(domain));
        body.put("slug", tag + "-r");
        ResponseEntity<Map> res = create(superAdmin(), body);
        long storeId = ((Number) ((Map) res.getBody().get("data")).get("id")).longValue();
        String email = "duena@" + domain;
        String first = TenantContext.callAs(storeId, () -> userRepository.findByEmail(email)).orElseThrow().getResetToken();

        HttpHeaders h = platform(superAdmin());
        assertThat(rest.exchange(url("/api/platform/stores/" + storeId + "/invite"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String second = TenantContext.callAs(storeId, () -> userRepository.findByEmail(email)).orElseThrow().getResetToken();
        assertThat(second).isNotBlank().isNotEqualTo(first);

        ResponseEntity<Map> withDomain = rest.exchange(url("/api/platform/stores/" + storeId + "/domains"), HttpMethod.POST,
                new HttpEntity<>(Map.of("domain", "https://www.otra-" + domain), h), Map.class);
        assertThat((List<String>) ((Map) withDomain.getBody().get("data")).get("domains")).contains("otra-" + domain);
        HttpHeaders visitor = new HttpHeaders();
        visitor.set("X-Store-Host", "otra-" + domain);
        ResponseEntity<Map> info = rest.exchange(url("/api/store-info"), HttpMethod.GET, new HttpEntity<>(visitor), Map.class);
        assertThat(((Map) info.getBody().get("data")).get("name")).isEqualTo("Papelería Luna");
    }

    @Test
    @DisplayName("from a store's domain the platform API does not exist, even for the super admin")
    @SuppressWarnings("rawtypes")
    void onlyOnPlatformHost() {
        HttpHeaders h = platform(superAdmin());
        h.set("X-Store-Host", "mareatextiles.com");
        ResponseEntity<Map> res = rest.exchange(url("/api/platform/stores"), HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        h.set("X-Store-Host", "localhost:4200"); // the local store
        assertThat(rest.exchange(url("/api/platform/stores"), HttpMethod.GET, new HttpEntity<>(h), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        h.set("X-Store-Host", PLATFORM_HOST + ":4200");
        assertThat(rest.exchange(url("/api/platform/stores"), HttpMethod.GET, new HttpEntity<>(h), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("only a super admin's session: not anonymous, not a store admin's or a customer's login")
    void onlySuperAdmin() {
        assertThat(create(null, store(tag + "-x.test")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(create(storeLogin("admin@test.com", "Admin123!"), store(tag + "-x.test")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(create(storeLogin("test@test.com", "Test123!"), store(tag + "-x.test")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a super admin is not a user of any store: its session opens no store's admin")
    @SuppressWarnings("rawtypes")
    void notAStoreUser() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(superAdmin());
        ResponseEntity<Map> users = rest.exchange(url("/api/admin/users"), HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // nor can it log in to a store
        ResponseEntity<Map> store = rest.postForEntity(url("/api/auth/login"),
                Map.of("email", "super@test.com", "password", "Super123!"), Map.class);
        assertThat(store.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    @DisplayName("added by hand with only the e-mail: sets the password with the link, and a new password ends old sessions")
    @SuppressWarnings("rawtypes")
    void passwordByLink() {
        String email = "jefa-" + tag + "@test.com";
        platformAdmins.save(PlatformAdmin.builder().email(email).build());
        assertThat(platformLogin(email, "", PLATFORM_HOST).getStatusCode()).isNotEqualTo(HttpStatus.OK);

        HttpHeaders h = platform(null);
        assertThat(rest.exchange(url("/api/platform/auth/forgot-password"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email.toUpperCase()), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = platformAdmins.findByEmailIgnoreCase(email).orElseThrow().getResetToken();
        assertThat(token).isNotBlank();

        assertThat(rest.exchange(url("/api/platform/auth/reset-password"), HttpMethod.POST,
                new HttpEntity<>(Map.of("token", token, "newPassword", "Jefa12345!"), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = platformToken(email, "Jefa12345!");
        HttpHeaders auth = platform(session);
        assertThat(rest.exchange(url("/api/platform/stores"), HttpMethod.GET, new HttpEntity<>(auth), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // the link only works once
        assertThat(rest.exchange(url("/api/platform/auth/reset-password"), HttpMethod.POST,
                new HttpEntity<>(Map.of("token", token, "newPassword", "Otra12345!"), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // a new password ends the old session
        rest.exchange(url("/api/platform/auth/forgot-password"), HttpMethod.POST, new HttpEntity<>(Map.of("email", email), h), Map.class);
        String again = platformAdmins.findByEmailIgnoreCase(email).orElseThrow().getResetToken();
        rest.exchange(url("/api/platform/auth/reset-password"), HttpMethod.POST,
                new HttpEntity<>(Map.of("token", again, "newPassword", "Nueva12345!"), h), Map.class);
        assertThat(rest.exchange(url("/api/platform/stores"), HttpMethod.GET, new HttpEntity<>(auth), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the platform login does not exist on a store's domain, and a wrong password is refused")
    void loginOnlyOnPlatformHost() {
        assertThat(platformLogin("super@test.com", "Super123!", "mareatextiles.com").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(platformLogin("super@test.com", "Super123!", "localhost").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(platformLogin("super@test.com", "mal-password", PLATFORM_HOST).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
