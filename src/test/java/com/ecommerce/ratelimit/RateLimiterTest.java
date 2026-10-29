package com.ecommerce.ratelimit;

import com.ecommerce.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private MutableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new RateLimiter(clock);
    }

    @Test
    @DisplayName("consume allows up to the limit, then rejects with the seconds left in the window")
    void consume_limit() {
        for (int i = 0; i < 30; i++) limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "1.1.1.1");
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    @DisplayName("the window resets after its period")
    void consume_windowResets() {
        for (int i = 0; i < 30; i++) limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "1.1.1.1");
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("subjects and rules are counted separately; emails ignore case")
    void separateCounters() {
        for (int i = 0; i < 3; i++) limiter.consume(RateLimit.FORGOT_PASSWORD_PER_EMAIL, "Ana@Example.com");

        assertThatThrownBy(() -> limiter.consume(RateLimit.FORGOT_PASSWORD_PER_EMAIL, "ana@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.consume(RateLimit.FORGOT_PASSWORD_PER_EMAIL, "otra@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.consume(RateLimit.FORGOT_PASSWORD_PER_IP, "ana@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkNotBlocked only rejects after the recorded failures reach the limit; reset clears them")
    void failures() {
        for (int i = 0; i < 9; i++) limiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2");
        assertThatCode(() -> limiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2")).doesNotThrowAnyException();

        limiter.recordFailure(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2");
        assertThatThrownBy(() -> limiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2"))
                .isInstanceOf(TooManyRequestsException.class);

        limiter.reset(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2");
        assertThatCode(() -> limiter.checkNotBlocked(RateLimit.LOGIN_FAILURES_PER_IP, "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does nothing when disabled")
    void disabled() {
        ReflectionTestUtils.setField(limiter, "enabled", false);
        for (int i = 0; i < 100; i++) limiter.consume(RateLimit.GUEST_ORDER_PER_IP, "3.3.3.3");
    }

    @Test
    @DisplayName("expired counters are evicted once there are many")
    void cleanup() {
        for (int i = 0; i < 10_000; i++) limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "ip-" + i);
        clock.advance(Duration.ofMinutes(2));
        limiter.consume(RateLimit.TRACK_ORDER_PER_IP, "fresh");

        @SuppressWarnings("unchecked")
        var windows = (java.util.Map<String, ?>) ReflectionTestUtils.getField(limiter, "windows");
        assertThat(windows).hasSize(1);
    }

    @Test
    @DisplayName("ClientIp uses the first X-Forwarded-For entry, else the connection address")
    void clientIp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        assertThat(ClientIp.of(req)).isEqualTo("10.0.0.1");

        req.addHeader("X-Forwarded-For", " 203.0.113.5 , 76.76.21.21, 10.0.0.1");
        assertThat(ClientIp.of(req)).isEqualTo("203.0.113.5");
    }
}
