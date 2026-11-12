package com.ecommerce.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runs scheduled work once per active store, each in that store's context. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantJobRunner {

    private final TenantRepository tenantRepository;

    public void forEachActiveTenant(Runnable work) {
        for (Tenant tenant : tenantRepository.findAllByStatus(Tenant.Status.ACTIVE)) {
            try {
                TenantContext.runAs(tenant.getId(), work);
            } catch (RuntimeException e) {
                // one store's failure must not stop the others
                log.error("Job failed for store {}: {}", tenant.getSlug(), e.getMessage(), e);
            }
        }
    }

    /** Null-safe for jobs built without Spring (unit tests): runs the work once in the current context. */
    public static void forEachActiveTenant(TenantJobRunner runner, Runnable work) {
        if (runner == null) work.run();
        else runner.forEachActiveTenant(work);
    }
}
