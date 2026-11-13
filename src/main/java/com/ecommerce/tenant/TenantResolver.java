package com.ecommerce.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the store a host name belongs to. Lookups are cached for a minute, so most requests do
 * not touch the database. Hosts no store claims fall back to {@code app.tenancy.default-tenant-id}
 * (Marea) when it is set, which keeps the backend's own Render address and old setups working.
 */
@Component
public class TenantResolver {

    /** Why a host has no store, for the frontend to tell apart (a suspended store shows its own page). */
    public static final String STORE_SUSPENDED = "STORE_SUSPENDED";
    public static final String STORE_NOT_FOUND = "STORE_NOT_FOUND";

    /** What a host resolves to: a store id, or empty with a code and the reason. */
    public record Resolution(Long tenantId, String code, String error) {
        static Resolution of(long id) { return new Resolution(id, null, null); }
        static Resolution fail(String code, String error) { return new Resolution(null, code, error); }
        public boolean ok() { return tenantId != null; }
    }

    private record Cached(Optional<TenantDomain> domain, Instant expires) {}

    private static final Duration TTL = Duration.ofMinutes(1);

    private final TenantDomainRepository domainRepository;
    private final TenantRepository tenantRepository;
    private final Long defaultTenantId;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public TenantResolver(TenantDomainRepository domainRepository,
                          TenantRepository tenantRepository,
                          @Value("${app.tenancy.default-tenant-id:1}") Long defaultTenantId) {
        this(domainRepository, tenantRepository, defaultTenantId, Clock.systemUTC());
    }

    TenantResolver(TenantDomainRepository domainRepository, TenantRepository tenantRepository,
                   Long defaultTenantId, Clock clock) {
        this.domainRepository = domainRepository;
        this.tenantRepository = tenantRepository;
        this.defaultTenantId = defaultTenantId != null && defaultTenantId > 0 ? defaultTenantId : null;
        this.clock = clock;
    }

    public Resolution resolve(String rawHost) {
        String host = normalize(rawHost);
        Optional<TenantDomain> match = host.isEmpty() ? Optional.empty() : lookup(host);
        if (match.isEmpty() && host.startsWith("www.")) match = lookup(host.substring(4));

        if (match.isPresent()) {
            Tenant tenant = match.get().getTenant();
            return tenant.getStatus() == Tenant.Status.ACTIVE
                    ? Resolution.of(tenant.getId())
                    : Resolution.fail(STORE_SUSPENDED, "Esta tienda no está disponible por el momento");
        }
        if (defaultTenantId != null) return Resolution.of(defaultTenantId);
        return Resolution.fail(STORE_NOT_FOUND, "Tienda no encontrada");
    }

    /** Forgets cached lookups, e.g. after a domain is added or a store suspended. */
    public void evictAll() {
        cache.clear();
    }

    private Optional<TenantDomain> lookup(String host) {
        Instant now = clock.instant();
        Cached cached = cache.get(host);
        if (cached != null && cached.expires().isAfter(now)) return cached.domain();
        Optional<TenantDomain> found = domainRepository.findByDomain(host);
        found.ifPresent(d -> d.getTenant().getStatus()); // load before caching
        if (cache.size() > 10_000) cache.clear(); // random hosts must not grow it without bound
        cache.put(host, new Cached(found, now.plus(TTL)));
        return found;
    }

    /** "WWW.Example.com:443, proxy" → "www.example.com". */
    static String normalize(String host) {
        if (host == null) return "";
        String h = host.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) return h; // IPv6 literal: never a store domain
        int colon = h.indexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

}
