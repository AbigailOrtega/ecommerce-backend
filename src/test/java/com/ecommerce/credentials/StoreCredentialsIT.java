package com.ecommerce.credentials;

import com.ecommerce.tenant.Tenant;
import com.ecommerce.tenant.TenantContext;
import com.ecommerce.tenant.TenantDomain;
import com.ecommerce.tenant.TenantDomainRepository;
import com.ecommerce.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin → Pagos y envíos: each store's own keys, checked with the provider, kept encrypted, never shared. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("e2e")
@DisplayName("Store payment keys — Integration")
class StoreCredentialsIT {

    @Autowired TestRestTemplate rest;
    @Autowired TenantRepository tenants;
    @Autowired TenantDomainRepository domains;
    @Autowired StoreCredentialsRepository credentials;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired com.ecommerce.repository.UserRepository users;
    @MockitoBean ProviderChecks checks;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.ecommerce.service.EmailService emails;
    @LocalServerPort int port;

    private final String tag = "k" + System.nanoTime();
    private String host;
    private String adminToken;

    private String url(String path) { return "http://localhost:" + port + path; }

    /** A second store (https, so webhooks are created) with its own admin. */
    @BeforeEach
    void store() throws Exception {
        host = tag + ".test";
        Tenant t = tenants.save(Tenant.builder().slug(tag).name("Papelería " + tag).baseUrl("https://" + host).build());
        domains.save(TenantDomain.builder().domain(host).tenant(t).build());
        TenantContext.runAs(t.getId(), () -> users.save(com.ecommerce.entity.User.builder()
                .email("duena@" + host).firstName("Duena").lastName("Luna").password(encoder.encode("Duena123!"))
                .role(com.ecommerce.entity.Role.ADMIN).unsubscribeToken(java.util.UUID.randomUUID().toString()).build()));
        adminToken = login(host, "duena@" + host, "Duena123!");
        when(checks.checkStripe(anyString())).thenReturn(null);
        when(checks.createStripeWebhook(anyString(), anyString())).thenReturn("whsec_created");
        when(checks.checkPayPal(anyString(), anyString(), anyBoolean())).thenReturn(true);
        when(checks.ensurePayPalWebhook(any(), anyString())).thenReturn("WH-123");
        when(checks.checkSkydropx(anyString(), anyString(), anyBoolean())).thenReturn(true);
    }

    @SuppressWarnings("rawtypes")
    private String login(String storeHost, String email, String password) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Store-Host", storeHost);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.exchange(url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), h), Map.class);
        assertThat(res.getStatusCode()).as("login " + email).isEqualTo(HttpStatus.OK);
        return (String) ((Map) res.getBody().get("data")).get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token, String storeHost) {
        // changes need the admin's password: the owner's, unless the test says otherwise
        if (method == HttpMethod.PUT && body instanceof Map<?, ?> m && !m.containsKey("currentPassword")) {
            Map<Object, Object> withPassword = new java.util.HashMap<>(m);
            withPassword.put("currentPassword", "Duena123!");
            body = withPassword;
        }
        HttpHeaders h = new HttpHeaders();
        h.set("X-Store-Host", storeHost);
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(url(path), method, new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private Map data(ResponseEntity<Map> res) { return (Map) res.getBody().get("data"); }

    @Test
    @DisplayName("a store without keys offers no card or PayPal payment (never another store's)")
    @SuppressWarnings("rawtypes")
    void noKeysNoPayments() {
        assertThat(data(call(HttpMethod.GET, "/api/payments/stripe/config", null, null, host)).get("publishableKey")).isEqualTo("");
        assertThat(data(call(HttpMethod.GET, "/api/payments/paypal/config", null, null, host)).get("clientId")).isEqualTo("");
        Map view = data(call(HttpMethod.GET, "/api/admin/payment-settings", null, adminToken, host));
        assertThat(((Map) view.get("stripe")).get("active")).isEqualTo(false);
    }

    @Test
    @DisplayName("Stripe keys are checked, kept encrypted, shown masked, and the webhook is created on the store's domain")
    @SuppressWarnings("rawtypes")
    void stripe() throws Exception {
        ResponseEntity<Map> res = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe",
                Map.of("publicKey", "pk_test_luna", "secretKey", "sk_test_luna_secret_1234"), adminToken, host);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        Map stripe = (Map) data(res).get("stripe");
        assertThat(stripe.get("active")).isEqualTo(true);
        assertThat(stripe.get("secretKeyEnd")).isEqualTo("…1234");
        assertThat(stripe.get("webhook")).isEqualTo(true);
        assertThat(res.getBody().toString()).doesNotContain("sk_test_luna_secret_1234");
        verify(checks).createStripeWebhook("sk_test_luna_secret_1234", "https://" + host + "/api/webhooks/stripe");

        // in the database only ciphertext
        long storeId = tenants.findAll().stream().filter(t -> tag.equals(t.getSlug())).findFirst().orElseThrow().getId();
        StoreCredentials row = TenantContext.callAs(storeId, () -> credentials.findFirstByOrderByIdAsc()).orElseThrow();
        assertThat(row.getStripeSecretKeyEnc()).startsWith("v1:").doesNotContain("luna_secret");

        // the checkout gets this store's publishable key; store #1's is untouched
        assertThat(data(call(HttpMethod.GET, "/api/payments/stripe/config", null, null, host)).get("publishableKey")).isEqualTo("pk_test_luna");
        assertThat(data(call(HttpMethod.GET, "/api/payments/stripe/config", null, null, "localhost")).get("publishableKey")).isNotEqualTo("pk_test_luna");

        // saving again without the secret keeps it
        res = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe", Map.of("publicKey", "pk_test_luna2"), adminToken, host);
        assertThat(((Map) data(res).get("stripe")).get("secretKeyEnd")).isEqualTo("…1234");

        // off
        res = call(HttpMethod.POST, "/api/admin/payment-settings/stripe/disable", Map.of("currentPassword", "Duena123!"), adminToken, host);
        assertThat(((Map) data(res).get("stripe")).get("active")).isEqualTo(false);
    }

    @Test
    @DisplayName("keys the provider refuses, or of different modes, are not saved")
    @SuppressWarnings("rawtypes")
    void refused() {
        when(checks.checkStripe("sk_test_wrong")).thenReturn("Stripe no aceptó la clave secreta: Invalid API Key");
        ResponseEntity<Map> res = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe",
                Map.of("publicKey", "pk_test_x", "secretKey", "sk_test_wrong"), adminToken, host);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("message").toString()).contains("Invalid API Key");

        res = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe",
                Map.of("publicKey", "pk_live_x", "secretKey", "sk_test_x"), adminToken, host);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(checks.checkPayPal("id", "bad", false)).thenReturn(false);
        res = call(HttpMethod.PUT, "/api/admin/payment-settings/paypal",
                Map.of("clientId", "id", "clientSecret", "bad", "live", false), adminToken, host);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map view = data(call(HttpMethod.GET, "/api/admin/payment-settings", null, adminToken, host));
        assertThat(((Map) view.get("stripe")).get("active")).isEqualTo(false);
        assertThat(((Map) view.get("paypal")).get("active")).isEqualTo(false);
    }

    @Test
    @DisplayName("PayPal and Skydropx: saved per store, PayPal's webhook set up")
    @SuppressWarnings("rawtypes")
    void paypalAndSkydropx() {
        ResponseEntity<Map> res = call(HttpMethod.PUT, "/api/admin/payment-settings/paypal",
                Map.of("clientId", "AeLuna", "clientSecret", "paypal-secret-9876", "live", false), adminToken, host);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        Map paypal = (Map) data(res).get("paypal");
        assertThat(paypal.get("active")).isEqualTo(true);
        assertThat(paypal.get("webhook")).isEqualTo(true);
        assertThat(data(call(HttpMethod.GET, "/api/payments/paypal/config", null, null, host)).get("clientId")).isEqualTo("AeLuna");

        res = call(HttpMethod.PUT, "/api/admin/payment-settings/skydropx",
                Map.of("clientId", "sky-id", "clientSecret", "sky-secret-5555", "sandbox", true, "webhookSecret", "hook"), adminToken, host);
        Map sky = (Map) data(res).get("skydropx");
        assertThat(sky.get("active")).isEqualTo(true);
        assertThat(sky.get("clientSecretEnd")).isEqualTo("…5555");
        assertThat(sky.get("webhook")).isEqualTo(true);
    }

    @Test
    @DisplayName("only the store's admins: a customer or another store's admin cannot see or change them")
    void onlyAdmins() {
        assertThat(call(HttpMethod.GET, "/api/admin/payment-settings", null, null, host).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String customer = login("localhost", "test@test.com", "Test123!");
        assertThat(call(HttpMethod.GET, "/api/admin/payment-settings", null, customer, "localhost").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Marea's admin token is for store #1: on this store it is no session at all
        String marea = login("localhost", "admin@test.com", "Admin123!");
        assertThat(call(HttpMethod.GET, "/api/admin/payment-settings", null, marea, host).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("every change needs the admin's password again, and every admin of the store is e-mailed")
    @SuppressWarnings("rawtypes")
    void passwordAndNotice() {
        long storeId = tenants.findAll().stream().filter(t -> tag.equals(t.getSlug())).findFirst().orElseThrow().getId();
        TenantContext.runAs(storeId, () -> users.save(com.ecommerce.entity.User.builder()
                .email("socio@" + host).firstName("Socio").lastName("Luna").password(encoder.encode("Socio123!"))
                .role(com.ecommerce.entity.Role.ADMIN).unsubscribeToken(java.util.UUID.randomUUID().toString()).build()));

        // without it, or with a wrong one: nothing changes, nobody is told
        for (String pw : new String[]{"", "otra-clave"}) {
            ResponseEntity<Map> res = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe",
                    Map.of("publicKey", "pk_test_luna", "secretKey", "sk_test_luna_1234", "currentPassword", pw), adminToken, host);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody().get("message").toString()).contains("contraseña");
        }
        assertThat(call(HttpMethod.POST, "/api/admin/payment-settings/paypal/disable", Map.of("currentPassword", "nop"), adminToken, host)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map) data(call(HttpMethod.GET, "/api/admin/payment-settings", null, adminToken, host)).get("stripe")).get("active")).isEqualTo(false);
        verify(emails, never()).sendPaymentKeysChangedEmail(any(), any(), any());

        // with it: saved, and both admins are told who did it
        ResponseEntity<Map> ok = call(HttpMethod.PUT, "/api/admin/payment-settings/stripe",
                Map.of("publicKey", "pk_test_luna", "secretKey", "sk_test_luna_1234"), adminToken, host);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(emails, timeout(5000)).sendPaymentKeysChangedEmail(
                argThat(list -> list != null && list.size() == 2
                        && list.containsAll(java.util.List.of("duena@" + host, "socio@" + host))),
                eq("duena@" + host), contains("Stripe"));
    }
}
