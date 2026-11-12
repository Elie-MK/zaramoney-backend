package com.zekodnix.zaramoney.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limits sensitive endpoints. Registered in the security chain after JWT authentication, so
 * per-user rules can key on the authenticated login. Anonymous requests are keyed by client IP.
 * <p>
 * Behind a reverse proxy (nginx), set {@code server.forward-headers-strategy: native} so
 * {@code request.getRemoteAddr()} is the real client IP (Tomcat only trusts X-Forwarded-For from internal proxies).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String name, HttpMethod method, String pattern, int limit, Duration window, boolean perUser) {}

    static final List<Rule> RULES = List.of(
        new Rule("login", HttpMethod.POST, "/api/authenticate", 10, Duration.ofMinutes(1), false),
        new Rule("register", HttpMethod.POST, "/api/register", 5, Duration.ofHours(1), false),
        new Rule("reset-init", HttpMethod.POST, "/api/account/reset-password/init", 5, Duration.ofHours(1), false),
        new Rule("reset-finish", HttpMethod.POST, "/api/account/reset-password/finish", 10, Duration.ofHours(1), false),
        new Rule("verify-account", HttpMethod.GET, "/api/bank-accounts/plus/accounts/*/verify", 30, Duration.ofMinutes(1), true),
        new Rule("send", HttpMethod.POST, "/api/plus/transaction-records/send", 10, Duration.ofMinutes(1), true),
        new Rule("withdrawal", HttpMethod.POST, "/api/plus/transaction-records/withdrawal", 10, Duration.ofMinutes(1), true),
        new Rule("set-pin", HttpMethod.PUT, "/api/plus/security/pin", 5, Duration.ofHours(1), true)
    );

    private final RateLimiter rateLimiter;
    private final boolean enabled;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter rateLimiter, boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : RULES) {
            if (rule.method().matches(request.getMethod()) && matcher.match(rule.pattern(), path)) {
                String key = "rl:" + rule.name() + ":" + clientKey(request, rule.perUser());
                Duration retryAfter = rateLimiter.tryAcquire(key, rule.limit(), rule.window());
                if (!retryAfter.isZero()) {
                    reject(response, path, retryAfter);
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request, boolean perUser) {
        if (perUser) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                return "user:" + auth.getName();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static void reject(HttpServletResponse response, String path, Duration retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response
            .getWriter()
            .write(
                "{\"type\":\"https://www.jhipster.tech/problem/problem-with-message\",\"title\":\"Too many requests\"," +
                "\"status\":429,\"message\":\"error.ratelimited\",\"path\":\"" +
                path.replace("\"", "") +
                "\"}"
            );
    }
}
