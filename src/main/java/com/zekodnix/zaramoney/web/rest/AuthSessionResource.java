package com.zekodnix.zaramoney.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.security.device.DeviceAuthException;
import com.zekodnix.zaramoney.security.device.NonceStore;
import com.zekodnix.zaramoney.service.SessionService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.UserService;
import com.zekodnix.zaramoney.service.dto.AuthTokensDTO;
import com.zekodnix.zaramoney.web.filter.DeviceBindingFilter;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** Login challenge, token refresh, logout and transaction-key re-registration (TODO.md item 13). */
@RestController
@RequestMapping("/api/auth")
public class AuthSessionResource {

    private final NonceStore nonceStore;
    private final SessionService sessionService;
    private final UserService userService;
    private final TransactionPinService transactionPinService;
    private final PasswordEncoder passwordEncoder;

    public AuthSessionResource(
        NonceStore nonceStore,
        SessionService sessionService,
        UserService userService,
        TransactionPinService transactionPinService,
        PasswordEncoder passwordEncoder
    ) {
        this.nonceStore = nonceStore;
        this.sessionService = sessionService;
        this.userService = userService;
        this.transactionPinService = transactionPinService;
        this.passwordEncoder = passwordEncoder;
    }

    /** {@code GET /api/auth/challenge} (public) → single-use nonce to sign at login, valid 2 minutes. */
    @GetMapping("/challenge")
    public Map<String, Object> challenge() {
        return Map.of(
            "nonce",
            nonceStore.issueChallenge(),
            "expiresAt",
            Instant.now().plus(NonceStore.CHALLENGE_TTL).toEpochMilli(),
            "serverTime",
            Instant.now().toEpochMilli()
        );
    }

    public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {}

    /**
     * {@code POST /api/auth/refresh} (public, but signed with the device key: X-Device-Id, X-Timestamp, X-Nonce, X-Signature).
     * Returns new access and refresh tokens; the old refresh token can no longer be used.
     */
    @PostMapping("/refresh")
    public AuthTokensDTO refresh(@RequestBody RefreshRequest body, HttpServletRequest request) {
        byte[] raw = (byte[]) request.getAttribute(DeviceBindingFilter.CACHED_BODY_ATTRIBUTE);
        if (raw == null) {
            throw DeviceAuthException.unauthorized("device_signature_missing", "Signed request expected");
        }
        return sessionService.refresh(body.refreshToken(), request, raw);
    }

    /** {@code POST /api/auth/logout} → ends the current session (its access and refresh tokens stop working at once). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        SecurityUtils.getCurrentSessionId().ifPresent(sid -> sessionService.revokeBySid(sid, SessionService.REASON_LOGOUT));
        return ResponseEntity.noContent().build();
    }

    public record TransactionKeyRequest(
        @NotBlank @Size(max = 512) String transactionPublicKey,
        @NotBlank String password,
        @NotBlank String pin
    ) {}

    /**
     * {@code POST /api/auth/transaction-key} (signed with the device key) → replaces the biometric transaction key,
     * e.g. after the user enrolled a new fingerprint (which invalidates the old key). Requires password and PIN.
     */
    @PostMapping("/transaction-key")
    public ResponseEntity<Void> replaceTransactionKey(@Valid @RequestBody TransactionKeyRequest body) {
        String sid = SecurityUtils.getCurrentSessionId()
            .orElseThrow(() -> DeviceAuthException.unauthorized("session_invalid", "Device session required"));
        var user = userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));
        if (!passwordEncoder.matches(body.password(), user.getPassword())) {
            throw ApiException.badRequest("Invalid password", "invalidpassword");
        }
        transactionPinService.verifyPin(user.getId(), body.pin());
        sessionService.replaceTransactionKey(sid, body.transactionPublicKey());
        return ResponseEntity.noContent().build();
    }
}
