package com.zekodnix.zaramoney.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.domain.enumeration.SessionType;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.security.device.CachedBodyHttpServletRequest;
import com.zekodnix.zaramoney.security.device.DeviceAuthException;
import com.zekodnix.zaramoney.security.device.DeviceSignatureVerifier;
import com.zekodnix.zaramoney.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after JWT authentication on every request (TODO.md item 13):
 * <ul>
 *   <li>every access token must reference an active session ({@code sid}), so revocation is immediate;</li>
 *   <li>DEVICE sessions: the request must be signed with the device key (and money operations also with the
 *   biometric transaction key);</li>
 *   <li>LEGACY sessions are refused once device binding is ENFORCE;</li>
 *   <li>{@code POST /api/auth/refresh} (no access token) only gets its body buffered here; the controller verifies it.</li>
 * </ul>
 * Not a Spring bean on purpose (it would also be registered as a plain servlet filter): created in SecurityConfiguration.
 */
public class DeviceBindingFilter extends OncePerRequestFilter {

    public static final String REFRESH_PATH = "/api/auth/refresh";
    public static final String CACHED_BODY_ATTRIBUTE = DeviceBindingFilter.class.getName() + ".body";

    /** Operations that need the biometric transaction-key signature in addition to the device signature. */
    static final Set<String> TRANSACTION_SIGNED = Set.of(
        "POST /api/plus/transaction-records/send",
        "POST /api/plus/transaction-records/withdrawal",
        "PUT /api/plus/security/pin"
    );

    private final SessionService sessionService;
    private final DeviceSignatureVerifier verifier;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceBindingFilter(SessionService sessionService, DeviceSignatureVerifier verifier, ApplicationProperties applicationProperties) {
        this.sessionService = sessionService;
        this.verifier = verifier;
        this.applicationProperties = applicationProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            String path = request.getRequestURI().substring(request.getContextPath().length());

            if (REFRESH_PATH.equals(path) && HttpMethod.POST.matches(request.getMethod())) {
                CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
                wrapped.setAttribute(CACHED_BODY_ATTRIBUTE, wrapped.getBody());
                chain.doFilter(wrapped, response);
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!(auth instanceof JwtAuthenticationToken jwt)) {
                chain.doFilter(request, response); // anonymous (public endpoints) or non-JWT test authentication
                return;
            }

            String sid = jwt.getToken().getClaimAsString(SecurityUtils.SESSION_ID_CLAIM);
            if (sid == null) {
                throw DeviceAuthException.unauthorized("session_invalid", "Token without session, please log in again");
            }
            var session = sessionService
                .findSnapshot(sid)
                .filter(SessionService.SessionSnapshot::active)
                .orElseThrow(() -> DeviceAuthException.unauthorized("session_revoked", "Session ended, please log in again"));

            if (session.type() == SessionType.ADMIN) {
                chain.doFilter(request, response);
                return;
            }
            if (session.type() == SessionType.LEGACY) {
                if (applicationProperties.getSecurity().getSessions().isEnforced()) {
                    throw DeviceAuthException.unauthorized("device_binding_required", "This app version is no longer supported, please update");
                }
                chain.doFilter(request, response);
                return;
            }

            // DEVICE session
            String did = jwt.getToken().getClaimAsString(SecurityUtils.DEVICE_ID_CLAIM);
            if (did == null || !did.equals(session.deviceId())) {
                throw DeviceAuthException.unauthorized("device_mismatch", "Token does not match the session device");
            }
            boolean multipart = request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/");
            HttpServletRequest forward = request;
            byte[] body = null;
            if (!multipart) {
                CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
                body = wrapped.getBody();
                forward = wrapped;
            }
            boolean needsTx = TRANSACTION_SIGNED.contains(request.getMethod().toUpperCase() + " " + path);
            verifier.verify(
                request,
                body,
                new DeviceSignatureVerifier.Expected(
                    session.sid(),
                    session.deviceId(),
                    session.devicePublicKey(),
                    session.transactionPublicKey(),
                    needsTx
                )
            );
            chain.doFilter(forward, response);
        } catch (DeviceAuthException e) {
            write(response, request, e);
        }
    }

    private void write(HttpServletResponse response, HttpServletRequest request, DeviceAuthException e) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://www.jhipster.tech/problem/problem-with-message");
        body.put("title", e.getMessage());
        body.put("status", e.getStatus().value());
        body.put("message", "error." + e.getCode());
        body.put("path", request.getRequestURI());
        if (e.getServerTime() != null) {
            body.put("serverTime", e.getServerTime().toEpochMilli());
        }
        response.setStatus(e.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
