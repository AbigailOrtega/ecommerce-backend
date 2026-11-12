package com.ecommerce.tenant;

import java.util.function.Supplier;

/**
 * The store (tenant) the current thread works for. TenantFilter sets it for each HTTP request from
 * the domain; jobs and startup code set it with {@link #runAs}. Every tenant-scoped entity query is
 * restricted to it by Hibernate ({@code @TenantId} on {@link com.ecommerce.entity.TenantScoped}).
 *
 * <p>With no tenant set, work belongs to {@link #DEFAULT_TENANT}: everything that existed before
 * multi-tenancy was Marea's, so code that never sets a store keeps behaving as it did. Work that
 * must cover every store (jobs) loops over them with {@link #runAs}.
 * {@link #ROOT} lifts the restriction for read-only, cross-store work (e.g. image cleanup).
 */
public final class TenantContext {

    /** Marea, the store that existed before multi-tenancy; old rows and tokens belong to it. */
    public static final long DEFAULT_TENANT = 1L;
    /** Sees every store; only for read-only maintenance. */
    public static final long ROOT = 0L;
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long get() {
        return CURRENT.get();
    }

    /** Current store; {@link #DEFAULT_TENANT} when none was set. */
    public static long current() {
        Long id = CURRENT.get();
        return id != null ? id : DEFAULT_TENANT;
    }

    public static void set(Long tenantId) {
        if (tenantId == null) CURRENT.remove();
        else CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(long tenantId, Supplier<T> work) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            set(previous);
        }
    }

    public static void runAs(long tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
