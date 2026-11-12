package com.zekodnix.zaramoney.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

    private final RateLimiter rateLimiter = new RateLimiter();
    private final AtomicInteger passed = new AtomicInteger();
    private final FilterChain chain = (req, res) -> passed.incrementAndGet();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, true);
        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/authenticate");
            req.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            lastStatus = res.getStatus();
            if (i == 10) {
                assertThat(res.getHeader("Retry-After")).isNotBlank();
                assertThat(res.getContentAsString()).contains("error.ratelimited");
            }
        }
        assertThat(passed.get()).isEqualTo(10);
        assertThat(lastStatus).isEqualTo(429);

        // another IP is not affected
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/authenticate");
        other.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(other, res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void sendIsLimitedPerUserNotPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, true);
        for (int i = 0; i < 10; i++) {
            send(filter, "alice", "10.0.0." + i);
        }
        assertThat(send(filter, "alice", "10.0.0.99").getStatus()).isEqualTo(429);
        assertThat(send(filter, "bob", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAndDisabledFilterAreNotLimited() throws Exception {
        RateLimitFilter disabled = new RateLimitFilter(rateLimiter, false);
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/authenticate");
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabled.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        RateLimitFilter enabled = new RateLimitFilter(rateLimiter, true);
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/account");
            MockHttpServletResponse res = new MockHttpServletResponse();
            enabled.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(RateLimitFilter filter, String user, String ip) throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/plus/transaction-records/send");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }
}
