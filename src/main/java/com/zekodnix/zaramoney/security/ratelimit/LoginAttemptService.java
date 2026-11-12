package com.zekodnix.zaramoney.security.ratelimit;

import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Per-account login lockout: after {@link #MAX_FAILURES} wrong passwords within {@link #WINDOW}, the account
 * cannot log in until the window expires (in-memory; see {@link RateLimiter}).
 * Note: an attacker can deliberately lock a victim out for {@link #WINDOW}; that is the accepted trade-off
 * against password guessing. Per-IP limits are applied separately by {@link RateLimitFilter}.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimiter rateLimiter;

    public LoginAttemptService(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void checkNotLocked(String login) {
        String key = key(login);
        if (rateLimiter.count(key) >= MAX_FAILURES) {
            throw ApiException.tooManyRequests(
                "Too many failed login attempts, try again later",
                "loginlocked",
                rateLimiter.retryAfter(key)
            );
        }
    }

    public void loginFailed(String login) {
        rateLimiter.tryAcquire(key(login), MAX_FAILURES, WINDOW);
    }

    public void loginSucceeded(String login) {
        rateLimiter.reset(key(login));
    }

    private static String key(String login) {
        return "login-failures:" + (login == null ? "" : login.trim().toLowerCase(Locale.ROOT));
    }
}
