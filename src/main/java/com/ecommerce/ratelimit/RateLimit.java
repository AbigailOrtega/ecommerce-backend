package com.ecommerce.ratelimit;

import java.time.Duration;

/**
 * Limits for the public endpoints that can be abused without an account. Per-IP limits depend on
 * reading the visitor's address correctly behind the proxies (see ClientIp) and can be switched off
 * on their own; per-email limits are always safe to apply.
 */
public enum RateLimit {
    LOGIN_FAILURES_PER_IP(10, Duration.ofMinutes(15), true),
    LOGIN_FAILURES_PER_EMAIL(20, Duration.ofMinutes(15), false),
    FORGOT_PASSWORD_PER_IP(5, Duration.ofHours(1), true),
    FORGOT_PASSWORD_PER_EMAIL(3, Duration.ofHours(1), false),
    TRACK_ORDER_PER_IP(30, Duration.ofMinutes(1), true),
    /** The pickup link of the emails: a few views and changes a minute. */
    PICKUP_MANAGE_PER_IP(30, Duration.ofMinutes(1), true),
    GUEST_ORDER_PER_IP(10, Duration.ofHours(1), true),
    GUEST_ORDER_PER_EMAIL(10, Duration.ofHours(1), false),
    /** A person browsing fast opens a few pages a minute; this only stops scripted inflation. */
    PAGE_VIEW_PER_IP(120, Duration.ofMinutes(1), true);

    final int limit;
    final Duration period;
    final boolean perIp;

    RateLimit(int limit, Duration period, boolean perIp) {
        this.limit = limit;
        this.period = period;
        this.perIp = perIp;
    }
}
