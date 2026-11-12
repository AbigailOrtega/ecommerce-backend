package com.ecommerce.credentials;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.tenant.Tenant;
import com.ecommerce.tenant.TenantContext;
import com.ecommerce.tenant.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * A store admin's Payments page: shows the store's keys masked, and saves new ones only after the
 * provider accepts them. Saving Stripe or PayPal keys also creates the webhook to the store's domain.
 * Blank secret fields keep the saved secret.
 */
@Slf4j
@Service
public class StoreCredentialsService {

    private final StoreCredentialsRepository repository;
    private final SecretBox box;
    private final StoreKeys keys;
    private final ProviderChecks checks;
    private final TenantRepository tenants;
    private final String defaultStoreUrl;

    public StoreCredentialsService(StoreCredentialsRepository repository, SecretBox box, StoreKeys keys,
                                   ProviderChecks checks, TenantRepository tenants,
                                   @Value("${app.mail.frontend-url:http://localhost:4200}") String defaultStoreUrl) {
        this.repository = repository;
        this.box = box;
        this.keys = keys;
        this.checks = checks;
        this.tenants = tenants;
        this.defaultStoreUrl = defaultStoreUrl;
    }

    public record StripeView(boolean active, String publicKey, String secretKeyEnd, boolean webhook, boolean testMode) {}
    public record PayPalView(boolean active, String clientId, String clientSecretEnd, boolean live, boolean webhook) {}
    public record SkydropxView(boolean active, String clientId, String clientSecretEnd, boolean sandbox, boolean webhook) {}
    public record View(StripeView stripe, PayPalView paypal, SkydropxView skydropx, boolean canSave,
                       String webhookBaseUrl, List<String> notes) {}

    public record StripeRequest(String publicKey, String secretKey, String webhookSecret) {}
    public record PayPalRequest(String clientId, String clientSecret, boolean live) {}
    public record SkydropxRequest(String clientId, String clientSecret, boolean sandbox, String webhookSecret) {}

    public View view() {
        return view(List.of());
    }

    private View view(List<String> notes) {
        StoreKeys.Stripe s = keys.stripe();
        StoreKeys.PayPal p = keys.paypal();
        StoreKeys.Skydropx k = keys.skydropx();
        return new View(
                new StripeView(s.ok(), s.publicKey(), end(s.secretKey()), notBlank(s.webhookSecret()),
                        s.secretKey() == null || s.secretKey().contains("_test_")),
                new PayPalView(p.ok(), p.clientId(), end(p.clientSecret()), p.live(), notBlank(p.webhookId())),
                new SkydropxView(k.ok(), k.clientId(), end(k.clientSecret()), k.sandbox(), notBlank(k.webhookSecret())),
                box.isConfigured(), storeUrl(), notes);
    }

    @Transactional
    public View saveStripe(StripeRequest req) {
        StoreCredentials row = row();
        String publicKey = trim(req.publicKey());
        String secret = trim(req.secretKey());
        String current = box.decrypt(row.getStripeSecretKeyEnc());
        if (publicKey == null || !publicKey.startsWith("pk_")) throw new BadRequestException("La clave publicable de Stripe empieza con pk_");
        if (secret == null && current == null) throw new BadRequestException("Falta la clave secreta de Stripe (sk_…)");
        String effective = secret != null ? secret : current;
        if (!effective.startsWith("sk_") && !effective.startsWith("rk_")) throw new BadRequestException("La clave secreta de Stripe empieza con sk_");
        if (publicKey.startsWith("pk_test_") != effective.contains("_test_")) {
            throw new BadRequestException("Las dos claves deben ser del mismo modo (las dos de prueba o las dos reales)");
        }
        if (secret != null) {
            String problem = checks.checkStripe(secret);
            if (problem != null) throw new BadRequestException(problem);
        }
        List<String> notes = new ArrayList<>();
        row.setStripePublicKey(publicKey);
        if (secret != null) row.setStripeSecretKeyEnc(box.encrypt(secret));

        String webhookSecret = trim(req.webhookSecret());
        if (webhookSecret != null) {
            if (!webhookSecret.startsWith("whsec_")) throw new BadRequestException("El secreto del webhook de Stripe empieza con whsec_");
            row.setStripeWebhookSecretEnc(box.encrypt(webhookSecret));
        } else if (secret != null || row.getStripeWebhookSecretEnc() == null) {
            String url = webhookUrl("stripe");
            if (url == null) {
                notes.add("El webhook de Stripe no se creó: la tienda no tiene un dominio https. Pega su secreto (whsec_…) a mano.");
            } else {
                try {
                    row.setStripeWebhookSecretEnc(box.encrypt(checks.createStripeWebhook(effective, url)));
                    notes.add("Webhook de Stripe creado: " + url);
                } catch (Exception e) {
                    log.warn("Stripe webhook not created for {}: {}", url, e.getMessage());
                    notes.add("No se pudo crear el webhook de Stripe (" + e.getMessage() + "). Créalo en Stripe con " + url + " y pega su secreto.");
                }
            }
        }
        return save(row, notes);
    }

    @Transactional
    public View savePayPal(PayPalRequest req) {
        StoreCredentials row = row();
        String clientId = trim(req.clientId());
        String secret = trim(req.clientSecret());
        String current = box.decrypt(row.getPaypalClientSecretEnc());
        if (clientId == null) throw new BadRequestException("Falta el Client ID de PayPal");
        if (secret == null && current == null) throw new BadRequestException("Falta el Secret de PayPal");
        String effective = secret != null ? secret : current;
        boolean accountChanged = secret != null || !clientId.equals(row.getPaypalClientId()) || req.live() != row.isPaypalLive();
        if (accountChanged && !checks.checkPayPal(clientId, effective, req.live())) {
            throw new BadRequestException("PayPal no aceptó esas claves" + (req.live() ? " (modo real)" : " (modo sandbox)"));
        }
        List<String> notes = new ArrayList<>();
        row.setPaypalClientId(clientId);
        if (secret != null) row.setPaypalClientSecretEnc(box.encrypt(secret));
        row.setPaypalLive(req.live());
        if (accountChanged || row.getPaypalWebhookId() == null) {
            String url = webhookUrl("paypal");
            if (url == null) {
                row.setPaypalWebhookId(null);
                notes.add("El webhook de PayPal no se creó: la tienda no tiene un dominio https.");
            } else {
                try {
                    row.setPaypalWebhookId(checks.ensurePayPalWebhook(new StoreKeys.PayPal(clientId, effective, req.live(), null), url));
                    notes.add("Webhook de PayPal listo: " + url);
                } catch (Exception e) {
                    log.warn("PayPal webhook not created for {}: {}", url, e.getMessage());
                    notes.add("No se pudo crear el webhook de PayPal; los pagos se confirman igual al regresar del pago.");
                }
            }
        }
        return save(row, notes);
    }

    @Transactional
    public View saveSkydropx(SkydropxRequest req) {
        StoreCredentials row = row();
        String clientId = trim(req.clientId());
        String secret = trim(req.clientSecret());
        String current = box.decrypt(row.getSkydropxClientSecretEnc());
        if (clientId == null) throw new BadRequestException("Falta el Client ID de Skydropx");
        if (secret == null && current == null) throw new BadRequestException("Falta el Client Secret de Skydropx");
        String effective = secret != null ? secret : current;
        boolean accountChanged = secret != null || !clientId.equals(row.getSkydropxClientId()) || req.sandbox() != row.isSkydropxSandbox();
        if (accountChanged && !checks.checkSkydropx(clientId, effective, req.sandbox())) {
            throw new BadRequestException("Skydropx no aceptó esas claves" + (req.sandbox() ? " (sandbox)" : " (producción)"));
        }
        row.setSkydropxClientId(clientId);
        if (secret != null) row.setSkydropxClientSecretEnc(box.encrypt(secret));
        row.setSkydropxSandbox(req.sandbox());
        String webhookSecret = trim(req.webhookSecret());
        if (webhookSecret != null) row.setSkydropxWebhookSecretEnc(box.encrypt(webhookSecret));
        return save(row, List.of());
    }

    /** Turns one provider off for this store (its keys are deleted). */
    @Transactional
    public View remove(String provider) {
        StoreCredentials row = row();
        switch (provider) {
            case "stripe" -> { row.setStripePublicKey(null); row.setStripeSecretKeyEnc(null); row.setStripeWebhookSecretEnc(null); }
            case "paypal" -> { row.setPaypalClientId(null); row.setPaypalClientSecretEnc(null); row.setPaypalWebhookId(null); }
            case "skydropx" -> { row.setSkydropxClientId(null); row.setSkydropxClientSecretEnc(null); row.setSkydropxWebhookSecretEnc(null); }
            default -> throw new BadRequestException("Proveedor desconocido");
        }
        return save(row, List.of());
    }

    private View save(StoreCredentials row, List<String> notes) {
        repository.save(row);
        keys.evict(TenantContext.current());
        return view(notes);
    }

    /** This store's row; store #1 starts from the environment's keys so saving one provider keeps the rest. */
    private StoreCredentials row() {
        if (!box.isConfigured()) throw new BadRequestException("Falta ENCRYPTION_KEY en el servidor: no se pueden guardar claves");
        return repository.findFirstByOrderByIdAsc().orElseGet(() ->
                TenantContext.current() == TenantContext.DEFAULT_TENANT ? fromEnvironment() : new StoreCredentials());
    }

    StoreCredentials fromEnvironment() {
        StoreKeys.All env = keys.environment();
        return StoreCredentials.builder()
                .stripePublicKey(trim(env.stripe().publicKey()))
                .stripeSecretKeyEnc(box.encrypt(trim(env.stripe().secretKey())))
                .stripeWebhookSecretEnc(box.encrypt(trim(env.stripe().webhookSecret())))
                .paypalClientId(trim(env.paypal().clientId()))
                .paypalClientSecretEnc(box.encrypt(trim(env.paypal().clientSecret())))
                .paypalLive(env.paypal().live())
                .paypalWebhookId(trim(env.paypal().webhookId()))
                .skydropxClientId(trim(env.skydropx().clientId()))
                .skydropxClientSecretEnc(box.encrypt(trim(env.skydropx().clientSecret())))
                .skydropxSandbox(env.skydropx().sandbox())
                .skydropxWebhookSecretEnc(box.encrypt(trim(env.skydropx().webhookSecret())))
                .build();
    }

    /** "https://www.tienda.com" — the store's own address (store #1 without one: the frontend URL). */
    String storeUrl() {
        return tenants.findById(TenantContext.current())
                .map(Tenant::getBaseUrl)
                .filter(StoreCredentialsService::notBlank)
                .orElse(defaultStoreUrl)
                .replaceAll("/+$", "");
    }

    /** The provider's webhook on this store's domain; null when the store has no https address. */
    private String webhookUrl(String provider) {
        String base = storeUrl();
        return base.startsWith("https://") ? base + "/api/webhooks/" + provider : null;
    }

    private static String end(String secret) {
        return secret == null || secret.length() < 4 ? null : "…" + secret.substring(secret.length() - 4);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
