package com.ecommerce.ratelimit;

import java.time.Duration;

/** Limits for the public endpoints that can be abused without an account. */
public enum RateLimit {
    LOGIN_FAILURES_PER_IP(10, Duration.ofMinutes(15)),
    LOGIN_FAILURES_PER_EMAIL(20, Duration.ofMinutes(15)),
    FORGOT_PASSWORD_PER_IP(5, Duration.ofHours(1)),
    FORGOT_PASSWORD_PER_EMAIL(3, Duration.ofHours(1)),
    TRACK_ORDER_PER_IP(30, Duration.ofMinutes(1)),
    GUEST_ORDER_PER_IP(10, Duration.ofHours(1));

    final int limit;
    final Duration period;

    RateLimit(int limit, Duration period) {
        this.limit = limit;
        this.period = period;
    }
}
