package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.SessionService;
import com.zekodnix.zaramoney.service.dto.SessionDTO;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Back-office session management and blocking. Under /api/admin/** → ROLE_ADMIN (SecurityConfiguration). */
@RestController
@RequestMapping("/api/admin/security")
public class AdminSecurityResource {

    private final SessionService sessionService;

    public AdminSecurityResource(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public record BlockRequest(@Size(max = 255) String reason) {}

    @GetMapping("/users/{login:.+}/sessions")
    public List<SessionDTO> userSessions(@PathVariable String login) {
        return sessionService.listActiveForLogin(login);
    }

    @PostMapping("/users/{login:.+}/revoke-sessions")
    public Map<String, Integer> revokeUserSessions(@PathVariable String login) {
        return Map.of("revoked", sessionService.revokeAllForLogin(login));
    }

    @DeleteMapping("/sessions/{sid}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sid) {
        sessionService.revokeBySid(sid, SessionService.REASON_ADMIN);
        return ResponseEntity.noContent().build();
    }

    /** Blocks the user: all sessions revoked, login and refresh refused until unblocked. */
    @PostMapping("/users/{login:.+}/block")
    public ResponseEntity<Void> blockUser(@PathVariable String login, @RequestBody(required = false) BlockRequest body) {
        sessionService.blockUser(login, body != null ? body.reason() : null, admin());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{login:.+}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable String login) {
        sessionService.unblockUser(login);
        return ResponseEntity.noContent().build();
    }

    /** Blocks a device id: its sessions are revoked and it can no longer log in. */
    @PostMapping("/devices/{deviceId}/block")
    public ResponseEntity<Void> blockDevice(@PathVariable String deviceId, @RequestBody(required = false) BlockRequest body) {
        sessionService.blockDevice(deviceId, body != null ? body.reason() : null, admin());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{deviceId}/block")
    public ResponseEntity<Void> unblockDevice(@PathVariable String deviceId) {
        sessionService.unblockDevice(deviceId);
        return ResponseEntity.noContent().build();
    }

    private static String admin() {
        return SecurityUtils.getCurrentUserLogin().orElse("?");
    }
}
