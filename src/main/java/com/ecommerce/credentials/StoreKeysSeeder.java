package com.ecommerce.credentials;

import com.ecommerce.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Once: copies the environment's Stripe/PayPal/Skydropx keys into store #1's own row, so from then
 * on Marea is a store like any other and its admin manages them in its panel. Needs ENCRYPTION_KEY;
 * without it (or with nothing to copy) store #1 simply keeps using the environment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreKeysSeeder implements ApplicationRunner {

    private final StoreCredentialsRepository repository;
    private final SecretBox box;
    private final StoreKeys keys;
    private final StoreCredentialsService credentials;

    @Override
    public void run(ApplicationArguments args) {
        if (!box.isConfigured()) return;
        StoreKeys.All env = keys.environment();
        if (!env.stripe().ok() && !env.paypal().ok() && !env.skydropx().ok()) return;
        try {
            TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> {
                if (repository.findFirstByOrderByIdAsc().isPresent()) return;
                repository.save(credentials.fromEnvironment());
                keys.evict(TenantContext.DEFAULT_TENANT);
                log.info("Store #1's payment and shipping keys copied from the environment to its own (encrypted) row");
            });
        } catch (RuntimeException e) {
            log.error("Could not copy store #1's keys from the environment (it keeps using them)", e);
        }
    }
}
