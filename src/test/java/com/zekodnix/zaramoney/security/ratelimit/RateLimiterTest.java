package com.zekodnix.zaramoney.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimiterTest {

    private MutableClock clock;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
        rateLimiter = new RateLimiter();
        rateLimiter.setClock(clock);
    }

    @Test
    void allowsUpToLimitThenRejectsUntilWindowResets() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isZero();
        }
        Duration retry = rateLimiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        assertThat(retry).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));

        clock.advance(Duration.ofSeconds(61));
        assertThat(rateLimiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isZero();
    }

    @Test
    void keysAreIndependentAndExpiredWindowsAreEvicted() {
        rateLimiter.tryAcquire("a", 1, Duration.ofMinutes(1));
        assertThat(rateLimiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isPositive();
        assertThat(rateLimiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isZero();

        clock.advance(Duration.ofMinutes(2));
        rateLimiter.evictExpired();
        assertThat(rateLimiter.count("a")).isZero();
    }

    @Test
    void loginIsLockedAfterMaxFailuresAndUnlockedOnSuccess() {
        LoginAttemptService attempts = new LoginAttemptService(rateLimiter);
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            attempts.checkNotLocked("Alice@Example.com");
            attempts.loginFailed("alice@example.com");
        }
        assertThatThrownBy(() -> attempts.checkNotLocked("ALICE@example.com"))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        clock.advance(LoginAttemptService.WINDOW.plusSeconds(1));
        attempts.checkNotLocked("alice@example.com");

        attempts.loginFailed("alice@example.com");
        attempts.loginSucceeded("alice@example.com");
        assertThat(rateLimiter.count("login-failures:alice@example.com")).isZero();
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
