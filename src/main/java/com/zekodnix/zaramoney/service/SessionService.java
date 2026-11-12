package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.domain.AccessBlock;
import com.zekodnix.zaramoney.domain.Authority;
import com.zekodnix.zaramoney.domain.RefreshToken;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserSession;
import com.zekodnix.zaramoney.domain.enumeration.AccessBlockType;
import com.zekodnix.zaramoney.domain.enumeration.SessionType;
import com.zekodnix.zaramoney.repository.AccessBlockRepository;
import com.zekodnix.zaramoney.repository.RefreshTokenRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.repository.UserSessionRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.security.device.CanonicalRequest;
import com.zekodnix.zaramoney.security.device.DeviceAuthException;
import com.zekodnix.zaramoney.security.device.DeviceKeys;
import com.zekodnix.zaramoney.security.device.DeviceSignatureVerifier;
import com.zekodnix.zaramoney.security.device.NonceStore;
import com.zekodnix.zaramoney.security.device.RevokeUserSessionsEvent;
import com.zekodnix.zaramoney.service.dto.AuthTokensDTO;
import com.zekodnix.zaramoney.service.dto.SessionDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.service.dto.LoginVM;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Login sessions, rotating refresh tokens, device binding, revocation and blocks (TODO.md item 13).
 * <ul>
 *   <li>DEVICE session: login proves possession of the device key; refresh tokens rotate on every use; reusing an old
 *   refresh token revokes the whole session.</li>
 *   <li>ADMIN session: admin without a device key; short access token, no refresh token.</li>
 *   <li>LEGACY session: old app without keys; only while device binding is OPTIONAL.</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String REASON_LOGOUT = "logout";
    public static final String REASON_REPLACED = "replaced_by_new_login";
    public static final String REASON_TOO_MANY_DEVICES = "too_many_devices";
    public static final String REASON_REFRESH_REUSE = "refresh_token_reuse";
    public static final String REASON_USER_REVOKED = "revoked_by_user";
    public static final String REASON_ADMIN = "revoked_by_admin";
    public static final String REASON_BLOCKED = "blocked";

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessBlockRepository accessBlockRepository;
    private final UserRepository userRepository;
    private final AccessTokenService accessTokenService;
    private final NonceStore nonceStore;
    private final DeviceSignatureVerifier signatureVerifier;
    private final ApplicationProperties applicationProperties;
    private final TransactionTemplate transactionTemplate;

    private final AuditService auditService;

    public SessionService(
        UserSessionRepository sessionRepository,
        RefreshTokenRepository refreshTokenRepository,
        AccessBlockRepository accessBlockRepository,
        UserRepository userRepository,
        AccessTokenService accessTokenService,
        NonceStore nonceStore,
        DeviceSignatureVerifier signatureVerifier,
        ApplicationProperties applicationProperties,
        PlatformTransactionManager transactionManager,
        AuditService auditService
    ) {
        this.auditService = auditService;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessBlockRepository = accessBlockRepository;
        this.userRepository = userRepository;
        this.accessTokenService = accessTokenService;
        this.nonceStore = nonceStore;
        this.signatureVerifier = signatureVerifier;
        this.applicationProperties = applicationProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private ApplicationProperties.Sessions config() {
        return applicationProperties.getSecurity().getSessions();
    }

    // ------------------------------------------------------------------------------------------------ login

    /** Called after the password was checked. Creates the session and returns the tokens. */
    @Transactional
    public AuthTokensDTO login(String login, LoginVM vm, HttpServletRequest request) {
        User user = userRepository
            .findOneWithAuthoritiesByLogin(login)
            .orElseThrow(() -> DeviceAuthException.unauthorized("unauthorized", "Unknown user"));
        if (isUserBlocked(user.getLogin())) {
            throw new DeviceAuthException(HttpStatus.FORBIDDEN, "account_blocked", "This account is blocked, contact support");
        }
        boolean admin = user.getAuthorities().stream().map(Authority::getName).anyMatch(AuthoritiesConstants.ADMIN::equals);
        Instant now = Instant.now();

        if (vm.hasDeviceBinding()) {
            return deviceLogin(user, vm, request, now);
        }
        if (admin) {
            UserSession session = newSession(user, SessionType.ADMIN, request, now, now.plusSeconds(config().getAdminSessionValiditySeconds()));
            long validity = Math.min(config().getAccessTokenValiditySeconds(), config().getAdminSessionValiditySeconds());
            return AuthTokensDTO.of(accessToken(user, session, validity), validity, null, null, session.getSid(), "ADMIN");
        }
        if (config().isEnforced()) {
            throw ApiException.badRequest("This app version is no longer supported, please update the app", "device_binding_required");
        }
        long validity = config().getLegacyTokenValiditySeconds();
        UserSession session = newSession(user, SessionType.LEGACY, request, now, now.plusSeconds(validity));
        return AuthTokensDTO.of(accessToken(user, session, validity), validity, null, null, session.getSid(), "LEGACY");
    }

    private AuthTokensDTO deviceLogin(User user, LoginVM vm, HttpServletRequest request, Instant now) {
        if (
            vm.getDeviceId() == null ||
            vm.getDevicePublicKey() == null ||
            vm.getTransactionPublicKey() == null ||
            vm.getNonce() == null ||
            vm.getSignature() == null
        ) {
            throw ApiException.badRequest(
                "deviceId, devicePublicKey, transactionPublicKey, nonce and signature are all required",
                "device_fields_missing"
            );
        }
        if (isDeviceBlocked(vm.getDeviceId())) {
            throw new DeviceAuthException(HttpStatus.FORBIDDEN, "device_blocked", "This device is blocked, contact support");
        }
        try {
            DeviceKeys.parseP256(vm.getDevicePublicKey());
            DeviceKeys.parseP256(vm.getTransactionPublicKey());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Public keys must be EC P-256 (base64 SubjectPublicKeyInfo)", "invalid_public_key");
        }
        if (!nonceStore.consumeChallenge(vm.getNonce())) {
            throw DeviceAuthException.unauthorized("challenge_invalid", "Login challenge missing, expired or already used");
        }
        String signed = CanonicalRequest.forLogin(vm.getNonce(), vm.getDeviceId(), vm.getUsername());
        if (!DeviceKeys.verify(vm.getDevicePublicKey(), signed, vm.getSignature())) {
            throw DeviceAuthException.unauthorized("device_signature_invalid", "Invalid login signature");
        }

        // One session per device: a new login on this device replaces any previous one (whatever the user)
        sessionRepository.findActiveByDeviceId(vm.getDeviceId(), now).forEach(s -> revoke(s, REASON_REPLACED, now));

        // Limit the number of devices per user: revoke the oldest ones
        List<UserSession> active = sessionRepository.findActiveByUserIdAndType(user.getId(), SessionType.DEVICE, now);
        for (int i = 0; i <= active.size() - config().getMaxDeviceSessions(); i++) {
            revoke(active.get(i), REASON_TOO_MANY_DEVICES, now);
        }

        UserSession session = new UserSession();
        session.setDeviceId(vm.getDeviceId());
        session.setDeviceName(vm.getDeviceName());
        session.setPlatform(vm.getPlatform());
        session.setDevicePublicKey(vm.getDevicePublicKey().trim());
        session.setTransactionPublicKey(vm.getTransactionPublicKey().trim());
        session = fill(session, user, SessionType.DEVICE, request, now, now.plus(Duration.ofDays(config().getSessionMaxLifetimeDays())));
        sessionRepository.save(session);

        String refresh = newRefreshToken(session, now, null);
        long validity = config().getAccessTokenValiditySeconds();
        LOG.info("New device session {} for user {} on device {} ({})", session.getSid(), user.getId(), vm.getDeviceId(), vm.getPlatform());
        // TODO: notify the user (push / email) that a new device logged in
        return AuthTokensDTO.of(accessToken(user, session, validity), validity, refresh, refreshExpiresIn(session, now), session.getSid(), "DEVICE");
    }

    // ------------------------------------------------------------------------------------------------ refresh

    /**
     * Exchanges a refresh token for a new access token and a new refresh token. The request must be signed with the
     * session's device key. Runs in its own transaction and commits revocations even when it then fails.
     */
    public AuthTokensDTO refresh(String rawRefreshToken, HttpServletRequest request, byte[] body) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank() || rawRefreshToken.length() > 200) {
            throw DeviceAuthException.unauthorized("refresh_token_invalid", "Invalid refresh token");
        }
        Object result = transactionTemplate.execute(status -> {
            try {
                return doRefresh(rawRefreshToken, request, body);
            } catch (DeviceAuthException e) {
                return e; // commit what was done (e.g. family revocation) and fail afterwards
            }
        });
        if (result instanceof DeviceAuthException e) {
            throw e;
        }
        return (AuthTokensDTO) result;
    }

    private AuthTokensDTO doRefresh(String rawRefreshToken, HttpServletRequest request, byte[] body) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository
            .findOneByTokenHashForUpdate(CanonicalRequest.sha256Hex(rawRefreshToken))
            .orElseThrow(() -> DeviceAuthException.unauthorized("refresh_token_invalid", "Invalid refresh token"));
        UserSession session = token.getSession();
        if (!session.isActive(now) || session.getSessionType() != SessionType.DEVICE) {
            throw DeviceAuthException.unauthorized("session_revoked", "Session ended, please log in again");
        }

        // Proof of possession first: a stolen refresh token alone can neither refresh nor trigger revocations
        signatureVerifier.verify(
            request,
            body,
            new DeviceSignatureVerifier.Expected(session.getSid(), session.getDeviceId(), session.getDevicePublicKey(), null, false)
        );

        if (token.getUsedAt() != null) {
            if (Duration.between(token.getUsedAt(), now).toSeconds() <= config().getRefreshReuseGraceSeconds()) {
                // two parallel refreshes from the same app: not theft, just tell the loser to use the newer token
                throw new DeviceAuthException(HttpStatus.CONFLICT, "refresh_in_progress", "Token already refreshed, use the latest one");
            }
            revoke(session, REASON_REFRESH_REUSE, now);
            LOG.warn("Refresh token reuse detected: session {} of user {} revoked", session.getSid(), session.getUser().getId());
            throw DeviceAuthException.unauthorized("refresh_token_reused", "Session ended for security reasons, please log in again");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw DeviceAuthException.unauthorized("refresh_token_expired", "Session expired, please log in again");
        }

        User user = session.getUser();
        if (!user.isActivated() || isUserBlocked(user.getLogin()) || isDeviceBlocked(session.getDeviceId())) {
            revoke(session, REASON_BLOCKED, now);
            throw new DeviceAuthException(HttpStatus.FORBIDDEN, "account_blocked", "This account or device is blocked");
        }

        String newRefresh = newRefreshToken(session, now, token);
        session.setLastUsedAt(now);
        session.setIp(clientIp(request));
        long validity = config().getAccessTokenValiditySeconds();
        return AuthTokensDTO.of(
            accessToken(user, session, validity),
            validity,
            newRefresh,
            refreshExpiresIn(session, now),
            session.getSid(),
            "DEVICE"
        );
    }

    // ------------------------------------------------------------------------------------------------ per-request

    /** Minimal, detached view of a session for the request filter. */
    public record SessionSnapshot(
        String sid,
        Long userId,
        SessionType type,
        String deviceId,
        String devicePublicKey,
        String transactionPublicKey,
        boolean active
    ) {}

    @Transactional(readOnly = true)
    public Optional<SessionSnapshot> findSnapshot(String sid) {
        Instant now = Instant.now();
        return sessionRepository
            .findOneBySid(sid)
            .map(s ->
                new SessionSnapshot(
                    s.getSid(),
                    s.getUser().getId(),
                    s.getSessionType(),
                    s.getDeviceId(),
                    s.getDevicePublicKey(),
                    s.getTransactionPublicKey(),
                    s.isActive(now)
                )
            );
    }

    /** Re-registers the biometric transaction key (e.g. after the user enrolled a new fingerprint). */
    @Transactional
    public void replaceTransactionKey(String sid, String transactionPublicKey) {
        try {
            DeviceKeys.parseP256(transactionPublicKey);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Public key must be EC P-256 (base64 SubjectPublicKeyInfo)", "invalid_public_key");
        }
        UserSession session = sessionRepository
            .findOneBySid(sid)
            .filter(s -> s.isActive(Instant.now()) && s.getSessionType() == SessionType.DEVICE)
            .orElseThrow(() -> DeviceAuthException.unauthorized("session_revoked", "Session ended"));
        session.setTransactionPublicKey(transactionPublicKey.trim());
        LOG.info("Transaction key replaced for session {}", sid);
    }

    // ------------------------------------------------------------------------------------------------ revocation

    @Transactional
    public void revokeBySid(String sid, String reason) {
        sessionRepository.findOneBySid(sid).ifPresent(s -> revoke(s, reason, Instant.now()));
    }

    /** For the user's own device list: only their sessions; 404 otherwise. */
    @Transactional
    public void revokeOwnSession(Long userId, String sid) {
        UserSession s = sessionRepository
            .findOneBySid(sid)
            .filter(x -> x.getUser().getId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Session not found", "sessionnotfound"));
        revoke(s, REASON_USER_REVOKED, Instant.now());
    }

    @Transactional
    public int revokeAllForUser(Long userId, String keepSid, String reason) {
        Instant now = Instant.now();
        int count = 0;
        for (UserSession s : sessionRepository.findActiveByUserId(userId, now)) {
            if (keepSid == null || !keepSid.equals(s.getSid())) {
                revoke(s, reason, now);
                count++;
            }
        }
        return count;
    }

    /** Password change/reset, PIN change or lock: revoke in the publisher's transaction. */
    @EventListener
    public void onRevokeUserSessions(RevokeUserSessionsEvent event) {
        int n = revokeAllForUser(event.userId(), event.keepSid(), event.reason());
        if (n > 0) {
            LOG.info("Revoked {} session(s) of user {} ({})", n, event.userId(), event.reason());
        }
    }

    @Transactional(readOnly = true)
    public List<SessionDTO> listActive(Long userId, String currentSid) {
        return sessionRepository.findActiveByUserId(userId, Instant.now()).stream().map(s -> toDto(s, currentSid)).toList();
    }

    // ------------------------------------------------------------------------------------------------ blocks (admin)

    @Transactional
    public void blockUser(String login, String reason, String admin) {
        User user = userRepository
            .findOneByLogin(login.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> ApiException.notFound("User not found", "usernotfound"));
        addBlock(AccessBlockType.USER, user.getLogin(), reason, admin);
        revokeAllForUser(user.getId(), null, REASON_BLOCKED);
        LOG.warn("User {} blocked by {}: {}", user.getLogin(), admin, reason);
        auditService.record(AuditAction.USER_BLOCKED, "USER", user.getLogin(), "reason=" + reason);
    }

    @Transactional
    public void unblockUser(String login) {
        accessBlockRepository
            .findOneByBlockTypeAndBlockValue(AccessBlockType.USER, login.toLowerCase(Locale.ROOT))
            .ifPresent(block -> {
                accessBlockRepository.delete(block);
                auditService.record(AuditAction.USER_UNBLOCKED, "USER", block.getBlockValue(), "was blocked for: " + block.getReason());
            });
    }

    @Transactional
    public void blockDevice(String deviceId, String reason, String admin) {
        addBlock(AccessBlockType.DEVICE, deviceId, reason, admin);
        Instant now = Instant.now();
        sessionRepository.findActiveByDeviceId(deviceId, now).forEach(s -> revoke(s, REASON_BLOCKED, now));
        LOG.warn("Device {} blocked by {}: {}", deviceId, admin, reason);
        auditService.record(AuditAction.DEVICE_BLOCKED, "DEVICE", deviceId, "reason=" + reason);
    }

    @Transactional
    public void unblockDevice(String deviceId) {
        accessBlockRepository
            .findOneByBlockTypeAndBlockValue(AccessBlockType.DEVICE, deviceId)
            .ifPresent(block -> {
                accessBlockRepository.delete(block);
                auditService.record(AuditAction.DEVICE_UNBLOCKED, "DEVICE", deviceId, "was blocked for: " + block.getReason());
            });
    }

    @Transactional(readOnly = true)
    public List<SessionDTO> listActiveForLogin(String login) {
        User user = userRepository
            .findOneByLogin(login.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> ApiException.notFound("User not found", "usernotfound"));
        return listActive(user.getId(), null);
    }

    @Transactional
    public int revokeAllForLogin(String login) {
        User user = userRepository
            .findOneByLogin(login.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> ApiException.notFound("User not found", "usernotfound"));
        int revoked = revokeAllForUser(user.getId(), null, REASON_ADMIN);
        auditService.record(AuditAction.SESSIONS_REVOKED, "USER", user.getLogin(), revoked + " session(s) revoked by an admin");
        return revoked;
    }

    public boolean isUserBlocked(String login) {
        return login != null && accessBlockRepository.existsByBlockTypeAndBlockValue(AccessBlockType.USER, login.toLowerCase(Locale.ROOT));
    }

    public boolean isDeviceBlocked(String deviceId) {
        return deviceId != null && accessBlockRepository.existsByBlockTypeAndBlockValue(AccessBlockType.DEVICE, deviceId);
    }

    /** Keeps sessions (audit trail) but deletes refresh tokens that expired or were rotated more than 30 days ago. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldRefreshTokens() {
        int n = refreshTokenRepository.deleteOldTokens(Instant.now().minus(Duration.ofDays(30)));
        if (n > 0) {
            LOG.info("Purged {} old refresh tokens", n);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private void addBlock(AccessBlockType type, String value, String reason, String admin) {
        if (accessBlockRepository.existsByBlockTypeAndBlockValue(type, value)) {
            return;
        }
        AccessBlock block = new AccessBlock();
        block.setBlockType(type);
        block.setBlockValue(value);
        block.setReason(reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason);
        block.setCreatedBy(admin);
        block.setCreatedAt(Instant.now());
        accessBlockRepository.save(block);
    }

    private void revoke(UserSession s, String reason, Instant now) {
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(now);
            s.setRevokeReason(reason);
            sessionRepository.save(s);
        }
    }

    private UserSession newSession(User user, SessionType type, HttpServletRequest request, Instant now, Instant expiresAt) {
        return sessionRepository.save(fill(new UserSession(), user, type, request, now, expiresAt));
    }

    private static UserSession fill(UserSession s, User user, SessionType type, HttpServletRequest request, Instant now, Instant expiresAt) {
        s.setSid(UUID.randomUUID().toString());
        s.setUser(user);
        s.setSessionType(type);
        s.setCreatedAt(now);
        s.setLastUsedAt(now);
        s.setExpiresAt(expiresAt);
        s.setIp(clientIp(request));
        String ua = request != null ? request.getHeader("User-Agent") : null;
        s.setUserAgent(ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua);
        return s;
    }

    /** @return the raw token (only ever returned to the client; the DB keeps its SHA-256) */
    private String newRefreshToken(UserSession session, Instant now, RefreshToken previous) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setSession(session);
        token.setTokenHash(CanonicalRequest.sha256Hex(raw));
        token.setCreatedAt(now);
        Instant byValidity = now.plus(Duration.ofDays(config().getRefreshTokenValidityDays()));
        token.setExpiresAt(byValidity.isBefore(session.getExpiresAt()) ? byValidity : session.getExpiresAt());
        token = refreshTokenRepository.save(token);

        if (previous != null) {
            previous.setUsedAt(now);
            previous.setReplacedById(token.getId());
            refreshTokenRepository.save(previous);
        }
        return raw;
    }

    private long refreshExpiresIn(UserSession session, Instant now) {
        Instant byValidity = now.plus(Duration.ofDays(config().getRefreshTokenValidityDays()));
        Instant end = byValidity.isBefore(session.getExpiresAt()) ? byValidity : session.getExpiresAt();
        return Math.max(0, Duration.between(now, end).toSeconds());
    }

    private String accessToken(User user, UserSession session, long validitySeconds) {
        List<String> authorities = user.getAuthorities().stream().map(Authority::getName).toList();
        return accessTokenService.create(user.getLogin(), user.getId(), authorities, session.getSid(), session.getDeviceId(), validitySeconds);
    }

    private static SessionDTO toDto(UserSession s, String currentSid) {
        return new SessionDTO(
            s.getSid(),
            s.getSessionType(),
            s.getDeviceId(),
            s.getDeviceName(),
            s.getPlatform(),
            s.getIp(),
            s.getCreatedAt(),
            s.getLastUsedAt(),
            s.getExpiresAt(),
            s.getRevokedAt(),
            s.getRevokeReason(),
            s.getSid().equals(currentSid)
        );
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getRemoteAddr();
        return ip != null && ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
