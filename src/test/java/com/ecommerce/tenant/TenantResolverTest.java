package com.ecommerce.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TenantResolver — which store a host belongs to")
class TenantResolverTest {

    private final TenantDomainRepository domains = mock(TenantDomainRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);

    private static Tenant store(long id, Tenant.Status status) {
        return Tenant.builder().id(id).slug("s" + id).name("Store " + id).status(status).build();
    }

    @Test
    @DisplayName("production (no default store): a domain no store claims is no store, not Marea")
    void noFallbackInProduction() {
        when(domains.findByDomain(anyString())).thenReturn(Optional.empty());
        TenantResolver resolver = new TenantResolver(domains, tenants, 0L, Clock.systemUTC());

        TenantResolver.Resolution r = resolver.resolve("ecommerce-backend.onrender.com");

        assertThat(r.ok()).isFalse();
        assertThat(r.code()).isEqualTo(TenantResolver.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("a store's own domain still resolves to it, www. or not")
    void ownDomain() {
        TenantDomain d = TenantDomain.builder().domain("mareatextiles.com").tenant(store(1, Tenant.Status.ACTIVE)).build();
        when(domains.findByDomain(anyString())).thenReturn(Optional.empty());
        when(domains.findByDomain("mareatextiles.com")).thenReturn(Optional.of(d));
        TenantResolver resolver = new TenantResolver(domains, tenants, 0L, Clock.systemUTC());

        assertThat(resolver.resolve("www.mareatextiles.com").tenantId()).isEqualTo(1L);
        assertThat(resolver.resolve("mareatextiles.com").tenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("locally (default store set): the fallback store is off while suspended")
    void suspendedFallback() {
        when(domains.findByDomain(anyString())).thenReturn(Optional.empty());
        when(tenants.findById(1L)).thenReturn(Optional.of(store(1, Tenant.Status.SUSPENDED)));
        TenantResolver resolver = new TenantResolver(domains, tenants, 1L, Clock.systemUTC());

        TenantResolver.Resolution r = resolver.resolve("localhost");

        assertThat(r.ok()).isFalse();
        assertThat(r.code()).isEqualTo(TenantResolver.STORE_SUSPENDED);
    }
}
