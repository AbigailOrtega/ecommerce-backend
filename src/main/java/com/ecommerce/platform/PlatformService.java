package com.ecommerce.platform;

import com.ecommerce.entity.StoreInfo;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.StoreInfoRepository;
import com.ecommerce.service.AdminAccountService;
import com.ecommerce.tenant.Tenant;
import com.ecommerce.tenant.TenantContext;
import com.ecommerce.tenant.TenantDomain;
import com.ecommerce.tenant.TenantDomainRepository;
import com.ecommerce.tenant.TenantRepository;
import com.ecommerce.tenant.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** The platform owner's operations: creating stores, suspending and reactivating them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final TenantRepository tenantRepository;
    private final TenantDomainRepository domainRepository;
    private final TenantResolver tenantResolver;
    private final StoreInfoRepository storeInfoRepository;
    private final AdminAccountService adminAccountService;

    public List<StoreSummary> list() {
        List<TenantDomain> domains = domainRepository.findAll();
        return tenantRepository.findAll().stream().map(t -> summary(t, domains)).toList();
    }

    /**
     * Creates the store, its domains, its store info (name) and its first admin, who gets an
     * invitation e-mail with a link to the new store to set a password.
     */
    public StoreSummary create(CreateStoreRequest req) {
        if (tenantRepository.findBySlug(req.slug()).isPresent()) {
            throw new BadRequestException("Ya existe una tienda con el nombre corto '" + req.slug() + "'");
        }
        Set<String> domains = new LinkedHashSet<>();
        for (String d : req.domains() != null ? req.domains() : List.<String>of()) {
            String domain = normalizeDomain(d);
            if (domain.isEmpty()) continue;
            if (domainRepository.findByDomain(domain).isPresent()) {
                throw new BadRequestException("El dominio " + domain + " ya es de otra tienda");
            }
            domains.add(domain);
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug(req.slug()).name(req.name().trim()).baseUrl(blankToNull(req.baseUrl())).build());
        for (String domain : domains) {
            domainRepository.save(TenantDomain.builder().domain(domain).tenant(tenant).build());
        }

        inStore(tenant.getId(), () -> {
            storeInfoRepository.save(StoreInfo.builder().id(tenant.getId()).name(tenant.getName()).build());
            adminAccountService.invite(req.adminEmail(), req.adminFirstName(), req.adminLastName(), "platform");
            return null;
        });
        tenantResolver.evictAll();
        log.info("Store created: {} ({}) domains={}", tenant.getName(), tenant.getSlug(), domains);
        return summary(tenant, domainRepository.findAll());
    }

    public StoreSummary setStatus(Long id, Tenant.Status status) {
        if (id == TenantContext.DEFAULT_TENANT && status == Tenant.Status.SUSPENDED) {
            throw new BadRequestException("La tienda #1 no se puede suspender");
        }
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Store", "id", id));
        tenant.setStatus(status);
        tenantRepository.save(tenant);
        tenantResolver.evictAll();
        return summary(tenant, domainRepository.findAll());
    }

    /**
     * Runs work as another store. The request's own persistence context belongs to the store the
     * request came through (open-in-view), so the work runs on a fresh thread with its own session.
     */
    private static <T> T inStore(long tenantId, Supplier<T> work) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return CompletableFuture.supplyAsync(() -> TenantContext.callAs(tenantId, work), executor).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    private static StoreSummary summary(Tenant t, List<TenantDomain> allDomains) {
        List<String> domains = new ArrayList<>();
        for (TenantDomain d : allDomains) if (d.getTenant().getId().equals(t.getId())) domains.add(d.getDomain());
        return new StoreSummary(t.getId(), t.getSlug(), t.getName(), t.getStatus().name(), t.getBaseUrl(), domains);
    }

    /** "https://WWW.Papeleria.com/" → "papeleria.com" (the resolver also matches www.). */
    static String normalizeDomain(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "");
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        int colon = d.indexOf(':');
        if (colon >= 0) d = d.substring(0, colon);
        if (d.startsWith("www.")) d = d.substring(4);
        return d;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
