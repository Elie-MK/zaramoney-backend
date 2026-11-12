package com.zekodnix.zaramoney.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.zekodnix.zaramoney.security.*;
import com.zekodnix.zaramoney.security.ratelimit.RateLimitFilter;
import com.zekodnix.zaramoney.security.ratelimit.RateLimiter;
import com.zekodnix.zaramoney.security.device.DeviceSignatureVerifier;
import com.zekodnix.zaramoney.service.SessionService;
import com.zekodnix.zaramoney.web.filter.DeviceBindingFilter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final ApplicationProperties applicationProperties;
    private final RateLimiter rateLimiter;
    private final SessionService sessionService;
    private final DeviceSignatureVerifier deviceSignatureVerifier;

    public SecurityConfiguration(
        ApplicationProperties applicationProperties,
        RateLimiter rateLimiter,
        @Lazy SessionService sessionService,
        DeviceSignatureVerifier deviceSignatureVerifier
    ) {
        this.applicationProperties = applicationProperties;
        this.rateLimiter = rateLimiter;
        this.sessionService = sessionService;
        this.deviceSignatureVerifier = deviceSignatureVerifier;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/authenticate")).permitAll()
                    // Login challenge, and refresh (authenticated by the refresh token + device signature, not by a JWT)
                    .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/auth/challenge")).permitAll()
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/auth/refresh")).permitAll()
                    .requestMatchers(mvc.pattern("/api/register")).permitAll()
                    .requestMatchers(mvc.pattern("/api/activate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/init")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/finish")).permitAll()
                    .requestMatchers(mvc.pattern("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    // Lists every user's login (email): admin only, to prevent user enumeration
                    .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/users")).hasAuthority(AuthoritiesConstants.ADMIN)
                    // Customer-facing custom endpoints nested under generated CRUD paths (must stay before the admin-only rules below)
                    .requestMatchers(mvc.pattern("/api/bank-accounts/plus/**")).authenticated()
                    .requestMatchers(mvc.pattern("/api/ledger-entries/plus/**")).authenticated()
                    // JHipster-generated CRUD endpoints: admin only (they allow raw edits of balances, ledger, etc.)
                    .requestMatchers(
                        mvc.pattern("/api/bank-accounts/**"),
                        mvc.pattern("/api/transaction-records/**"),
                        mvc.pattern("/api/ledger-entries/**"),
                        mvc.pattern("/api/transaction-fees/**"),
                        mvc.pattern("/api/transaction-limits/**"),
                        mvc.pattern("/api/idempotency-records/**"),
                        mvc.pattern("/api/notifications/**"),
                        mvc.pattern("/api/user-details-accounts/**")
                    ).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/**")).authenticated()
                    .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            // After JWT authentication so per-user limits know who is calling
            .addFilterAfter(
                new RateLimitFilter(rateLimiter, applicationProperties.getSecurity().isRateLimitEnabled()),
                BearerTokenAuthenticationFilter.class
            )
            // Session must be active; DEVICE sessions must sign every request (TODO.md item 13)
            .addFilterAfter(new DeviceBindingFilter(sessionService, deviceSignatureVerifier, applicationProperties), RateLimitFilter.class);
        return http.build();
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }
}
