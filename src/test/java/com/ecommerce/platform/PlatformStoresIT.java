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

/** The platform owner creates a store over the API; it answers on its domain with its own name and admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.platform.api-key=" + PlatformStoresIT.KEY)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Platform: creating stores — Integration")
class PlatformStoresIT {

    static final String KEY = "test-platform-key-0123456789abcdef0123";

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @LocalServerPort int port;

    private final String tag = "p" + System.nanoTime();

    private String url(String path) { return "http://localhost:" + port + path; }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> create(String key, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) h.set("X-Platform-Key", key);
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
        ResponseEntity<Map> res = create(KEY, store(domain));
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
        assertThat(create(KEY, again).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resending the invitation gives the store's admin a fresh link; a domain can be added")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void resendAndDomain() {
        String domain = tag + "-r.test";
        Map<String, Object> body = new java.util.HashMap<>(store(domain));
        body.put("slug", tag + "-r");
        ResponseEntity<Map> res = create(KEY, body);
        long storeId = ((Number) ((Map) res.getBody().get("data")).get("id")).longValue();
        String email = "duena@" + domain;
        String first = TenantContext.callAs(storeId, () -> userRepository.findByEmail(email)).orElseThrow().getResetToken();

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Platform-Key", KEY);
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
    @DisplayName("without the platform key nothing is created")
    void needsKey() {
        assertThat(create(null, store(tag + "-x.test")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(create("wrong-key-wrong-key-wrong-key-wrong", store(tag + "-x.test")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
