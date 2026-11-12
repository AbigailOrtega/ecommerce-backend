package com.ecommerce.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which store's rows a new session sees (see {@link TenantContext}). Hibernate
 * creates it from {@code spring.jpa.properties.hibernate.tenant_identifier_resolver}, so test
 * slices that skip component scanning get it too.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return TenantContext.current();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return tenantId != null && tenantId == TenantContext.ROOT;
    }
}
