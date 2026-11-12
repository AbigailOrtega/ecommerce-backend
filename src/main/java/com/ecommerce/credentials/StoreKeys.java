package com.ecommerce.credentials;

import com.ecommerce.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The current store's keys for Stripe, PayPal and Skydropx, decrypted. A store without its own
 * keys has none: its payments and shipping are off, never another store's accounts.
 *
 * <p>The one exception: store #1 (Marea) keeps using the server's environment variables until it
 * has its own row (StoreKeysSeeder copies them there at startup), so it never stops charging.
 */
@Slf4j
@Service
public class StoreKeys {

    public record Stripe(String publicKey, String secretKey, String webhookSecret) {
        public boolean ok() { return notBlank(secretKey) && notBlank(publicKey); }
    }

    public record PayPal(String clientId, String clientSecret, boolean live, String webhookId) {
        public boolean ok() { return notBlank(clientId) && notBlank(clientSecret); }
    }

    public record Skydropx(String clientId, String clientSecret, boolean sandbox, String webhookSecret) {
        public boolean ok() { return notBlank(clientId) && notBlank(clientSecret); }
    }

    public record All(Stripe stripe, PayPal paypal, Skydropx skydropx) {}

    private static final All NONE = new All(new Stripe(null, null, null),
            new PayPal(null, null, false, null), new Skydropx(null, null, true, null));

    private final StoreCredentialsRepository repository;
    private final SecretBox box;
    private final All environment;
    private final Map<Long, All> cache = new ConcurrentHashMap<>();

    public StoreKeys(StoreCredentialsRepository repository, SecretBox box,
                     @Value("${app.stripe.public-key:}") String stripePublic,
                     @Value("${app.stripe.secret-key:}") String stripeSecret,
                     @Value("${app.stripe.webhook-secret:}") String stripeWebhook,
                     @Value("${app.paypal.client-id:}") String paypalId,
                     @Value("${app.paypal.client-secret:}") String paypalSecret,
                     @Value("${app.paypal.mode:sandbox}") String paypalMode,
                     @Value("${app.paypal.webhook-id:}") String paypalWebhookId,
                     @Value("${app.skydropx.client-id:}") String skydropxId,
                     @Value("${app.skydropx.client-secret:}") String skydropxSecret,
                     @Value("${app.skydropx.sandbox:true}") boolean skydropxSandbox,
                     @Value("${app.skydropx.webhook-secret:}") String skydropxWebhook) {
        this.repository = repository;
        this.box = box;
        this.environment = new All(new Stripe(stripePublic, stripeSecret, stripeWebhook),
                new PayPal(paypalId, paypalSecret, "live".equalsIgnoreCase(paypalMode), paypalWebhookId),
                new Skydropx(skydropxId, skydropxSecret, skydropxSandbox, skydropxWebhook));
    }

    public Stripe stripe() { return current().stripe(); }
    public PayPal paypal() { return current().paypal(); }
    public Skydropx skydropx() { return current().skydropx(); }

    /** The server's environment keys (what store #1 used before it had its own row). */
    All environment() { return environment; }

    /** Called after a store's keys change. */
    public void evict(long tenantId) {
        cache.remove(tenantId);
    }

    private All current() {
        long tenant = TenantContext.current();
        return cache.computeIfAbsent(tenant, this::load);
    }

    private All load(long tenant) {
        return TenantContext.callAs(tenant, () -> repository.findFirstByOrderByIdAsc()
                .map(this::decrypt)
                .orElse(tenant == TenantContext.DEFAULT_TENANT ? environment : NONE));
    }

    private All decrypt(StoreCredentials c) {
        try {
            return new All(
                    new Stripe(c.getStripePublicKey(), box.decrypt(c.getStripeSecretKeyEnc()), box.decrypt(c.getStripeWebhookSecretEnc())),
                    new PayPal(c.getPaypalClientId(), box.decrypt(c.getPaypalClientSecretEnc()), c.isPaypalLive(), c.getPaypalWebhookId()),
                    new Skydropx(c.getSkydropxClientId(), box.decrypt(c.getSkydropxClientSecretEnc()), c.isSkydropxSandbox(),
                            box.decrypt(c.getSkydropxWebhookSecretEnc())));
        } catch (IllegalStateException e) {
            // never fall back to another account: this store's payments are off until fixed
            log.error("Store {} keys cannot be read: {}", c.getTenantId(), e.getMessage());
            return NONE;
        }
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
