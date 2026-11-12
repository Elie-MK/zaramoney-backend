package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.SessionService;
import com.zekodnix.zaramoney.service.UserService;
import com.zekodnix.zaramoney.service.dto.SessionDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The current user's devices / sessions. */
@RestController
@RequestMapping("/api/account/sessions")
public class AccountSessionResource {

    private final SessionService sessionService;
    private final UserService userService;

    public AccountSessionResource(SessionService sessionService, UserService userService) {
        this.sessionService = sessionService;
        this.userService = userService;
    }

    @GetMapping
    public List<SessionDTO> list() {
        return sessionService.listActive(currentUserId(), SecurityUtils.getCurrentSessionId().orElse(null));
    }

    /** Logs out one of the user's devices. */
    @DeleteMapping("/{sid}")
    public ResponseEntity<Void> revoke(@PathVariable String sid) {
        sessionService.revokeOwnSession(currentUserId(), sid);
        return ResponseEntity.noContent().build();
    }

    /** Logs out everywhere ({@code keepCurrent=true} keeps this device logged in). */
    @PostMapping("/revoke-all")
    public Map<String, Integer> revokeAll(@RequestParam(defaultValue = "false") boolean keepCurrent) {
        String keep = keepCurrent ? SecurityUtils.getCurrentSessionId().orElse(null) : null;
        return Map.of("revoked", sessionService.revokeAllForUser(currentUserId(), keep, SessionService.REASON_USER_REVOKED));
    }

    private Long currentUserId() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"))
            .getId();
    }
}
