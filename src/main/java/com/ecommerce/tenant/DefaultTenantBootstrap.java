package com.ecommerce.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes sure store #1 (Marea) exists before anything else starts. In production the V10 migration
 * creates it; databases Hibernate builds from scratch (dev, tests) start empty.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DefaultTenantBootstrap implements ApplicationRunner {

    private final TenantRepository tenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.existsById(TenantContext.DEFAULT_TENANT)) return;
        Tenant marea = tenantRepository.save(Tenant.builder().slug("marea").name("Marea Textiles").build());
        if (marea.getId() != TenantContext.DEFAULT_TENANT) {
            throw new IllegalStateException("Store #1 must be the first store, got id " + marea.getId());
        }
        log.info("Created store #1 ({})", marea.getName());
    }
}
