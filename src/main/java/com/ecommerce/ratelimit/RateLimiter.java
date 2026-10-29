package com.ecommerce.ratelimit;

import com.ecommerce.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window counters, one per (limit, subject). The backend runs as a single
 * instance on Render, so memory is enough; counters reset on restart.
 */
@Slf4j
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Autowired
    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one request and rejects it once the limit for the current window is exceeded. */
    public void consume(RateLimit rule, String subject) {
        if (!enabled) return;
        Window w = windows.compute(key(rule, subject), (k, old) ->
                isActive(old, rule) ? new Window(old.start(), old.count() + 1) : new Window(clock.instant(), 1));
        if (w.count() > rule.limit) reject(rule, subject, w);
        cleanupIfLarge();
    }

    /** Rejects without counting when the subject already used up its attempts (e.g. failed logins). */
    public void checkNotBlocked(RateLimit rule, String subject) {
        if (!enabled) return;
        Window w = windows.get(key(rule, subject));
        if (isActive(w, rule) && w.count() >= rule.limit) reject(rule, subject, w);
    }

    /** Counts a failed attempt, to be checked later with {@link #checkNotBlocked}. */
    public void recordFailure(RateLimit rule, String subject) {
        if (!enabled) return;
        windows.compute(key(rule, subject), (k, old) ->
                isActive(old, rule) ? new Window(old.start(), old.count() + 1) : new Window(clock.instant(), 1));
        cleanupIfLarge();
    }

    public void reset(RateLimit rule, String subject) {
        windows.remove(key(rule, subject));
    }

    private boolean isActive(Window w, RateLimit rule) {
        return w != null && clock.instant().isBefore(w.start().plus(rule.period));
    }

    private void reject(RateLimit rule, String subject, Window w) {
        long retryAfter = Math.max(1, w.start().plus(rule.period).getEpochSecond() - clock.instant().getEpochSecond());
        log.warn("Rate limit {} reached for {}", rule, subject);
        throw new TooManyRequestsException(retryAfter);
    }

    private void cleanupIfLarge() {
        if (windows.size() < CLEANUP_THRESHOLD) return;
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(ruleOf(e.getKey()).period)));
    }

    private static String key(RateLimit rule, String subject) {
        return rule.name() + ":" + (subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT));
    }

    private static RateLimit ruleOf(String key) {
        return RateLimit.valueOf(key.substring(0, key.indexOf(':')));
    }
}
