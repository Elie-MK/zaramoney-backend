package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.security.ratelimit.LoginAttemptService;
import com.zekodnix.zaramoney.service.SessionService;
import com.zekodnix.zaramoney.service.dto.AuthTokensDTO;
import com.zekodnix.zaramoney.service.dto.LoginVM;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * Login. Creates a session (TODO.md item 13):
 * <ul>
 *   <li>with device fields (deviceId, keys, challenge nonce and signature) → DEVICE session: access + refresh token;</li>
 *   <li>admin without device fields → ADMIN session: short access token only;</li>
 *   <li>customer without device fields → LEGACY session (only while device binding is OPTIONAL).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class AuthenticateController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateController.class);

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;

    public AuthenticateController(
        AuthenticationManagerBuilder authenticationManagerBuilder,
        LoginAttemptService loginAttemptService,
        SessionService sessionService
    ) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthTokensDTO> authorize(@Valid @RequestBody LoginVM loginVM, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
            loginVM.getUsername(),
            loginVM.getPassword()
        );

        loginAttemptService.checkNotLocked(loginVM.getUsername());
        Authentication authentication;
        try {
            authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (AuthenticationException e) {
            loginAttemptService.loginFailed(loginVM.getUsername());
            throw e;
        }
        loginAttemptService.loginSucceeded(loginVM.getUsername());

        AuthTokensDTO tokens = sessionService.login(authentication.getName(), loginVM, request);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(tokens.accessToken());
        return new ResponseEntity<>(tokens, httpHeaders, HttpStatus.OK);
    }

    /**
     * {@code GET /authenticate} : check if the user is authenticated.
     *
     * @return the {@link ResponseEntity} with status {@code 204 (No Content)},
     * or with status {@code 401 (Unauthorized)} if not authenticated.
     */
    @GetMapping("/authenticate")
    public ResponseEntity<Void> isAuthenticated(Principal principal) {
        LOG.debug("REST request to check if the current user is authenticated");
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.NO_CONTENT).build();
    }
}
