package com.zekodnix.zaramoney.security.jwt;

import com.zekodnix.zaramoney.domain.UserSession;
import com.zekodnix.zaramoney.domain.enumeration.SessionType;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.repository.UserSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.zekodnix.zaramoney.security.jwt.JwtAuthenticationTestUtils.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@AutoConfigureMockMvc
@AuthenticationIntegrationTest
class TokenAuthenticationIT {

    @Autowired
    private MockMvc mvc;

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void testLoginWithValidToken() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UserSession session = tx.execute(s -> {
            UserSession us = new UserSession();
            us.setSid(UUID.randomUUID().toString());
            us.setUser(userRepository.findAll().get(0));
            us.setSessionType(SessionType.ADMIN);
            us.setCreatedAt(Instant.now());
            us.setExpiresAt(Instant.now().plusSeconds(600));
            return userSessionRepository.save(us);
        });
        try {
            expectOk(createValidTokenForSession(jwtKey, "anonymous", session.getSid()));
        } finally {
            tx.executeWithoutResult(s -> userSessionRepository.deleteById(session.getId()));
        }
    }

    @Test
    void testTokenWithoutSessionIsRejected() throws Exception {
        // a correctly signed token is not enough: it must belong to an active session (TODO.md item 13)
        expectUnauthorized(createValidToken(jwtKey));
    }

    @Test
    void testTokenOfUnknownSessionIsRejected() throws Exception {
        expectUnauthorized(createValidTokenForSession(jwtKey, "anonymous", UUID.randomUUID().toString()));
    }

    @Test
    void testReturnFalseWhenJWThasInvalidSignature() throws Exception {
        expectUnauthorized(createTokenWithDifferentSignature());
    }

    @Test
    void testReturnFalseWhenJWTisMalformed() throws Exception {
        expectUnauthorized(createSignedInvalidJwt(jwtKey));
    }

    @Test
    void testReturnFalseWhenJWTisExpired() throws Exception {
        expectUnauthorized(createExpiredToken(jwtKey));
    }

    private void expectOk(String token) throws Exception {
        mvc
            .perform(MockMvcRequestBuilders.get("/api/authenticate").header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isNoContent());
    }

    private void expectUnauthorized(String token) throws Exception {
        mvc
            .perform(MockMvcRequestBuilders.get("/api/authenticate").header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isUnauthorized());
    }
}
