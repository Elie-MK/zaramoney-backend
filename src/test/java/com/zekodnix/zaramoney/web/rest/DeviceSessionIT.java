package com.zekodnix.zaramoney.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Sessions.DeviceBindingMode;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.AuthorityRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.security.device.CanonicalRequest;
import com.zekodnix.zaramoney.security.device.TestDeviceKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TODO.md item 13 end to end, through the real security filter chain with real JWTs:
 * challenge → device login → signed requests → refresh rotation / reuse detection → revocation → blocks.
 */
@IntegrationTest
@AutoConfigureMockMvc
class DeviceSessionIT {

    private static final String PASSWORD = "Str0ng-Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationProperties applicationProperties;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final Set<String> deviceIds = new HashSet<>();

    /** The app on one phone: device id + two Secure Enclave keys + current tokens. */
    final class Phone {

        final String deviceId = "dev-" + UUID.randomUUID();
        final TestDeviceKey deviceKey = TestDeviceKey.generate();
        final TestDeviceKey txKey = TestDeviceKey.generate();
        String accessToken;
        String refreshToken;
        String sid;

        Phone() {
            deviceIds.add(deviceId);
        }

        ResultActions login(String username) throws Exception {
            String nonce = challenge();
            return login(username, nonce, deviceKey.sign(CanonicalRequest.forLogin(nonce, deviceId, username)));
        }

        ResultActions login(String username, String nonce, String signature) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("password", PASSWORD);
            body.put("deviceId", deviceId);
            body.put("deviceName", "Test phone");
            body.put("platform", "ios");
            body.put("devicePublicKey", deviceKey.publicKey());
            body.put("transactionPublicKey", txKey.publicKey());
            body.put("nonce", nonce);
            body.put("signature", signature);
            ResultActions r = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(json(body)));
            String content = r.andReturn().getResponse().getContentAsString();
            if (r.andReturn().getResponse().getStatus() == 200) {
                take(content);
            }
            return r;
        }

        void take(String tokensJson) throws Exception {
            JsonNode n = objectMapper.readTree(tokensJson);
            accessToken = n.get("access_token").asText();
            refreshToken = n.has("refresh_token") ? n.get("refresh_token").asText() : null;
            sid = n.get("session_id").asText();
        }

        /** A request signed like the app does it. */
        MockHttpServletRequestBuilder signed(HttpMethod method, String uri, byte[] body, boolean withTxKey) {
            String ts = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString();
            String path = uri; // the tests never use query strings except where stated
            String canonical = CanonicalRequest.forRequest(method.name(), path, ts, nonce, deviceId, CanonicalRequest.bodyHash(body));
            MockHttpServletRequestBuilder b = request(method, uri)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", ts)
                .header("X-Nonce", nonce)
                .header("X-Signature", deviceKey.sign(canonical))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
            if (withTxKey) {
                b.header("X-Tx-Signature", txKey.sign(canonical));
            }
            if (accessToken != null) {
                b.header("Authorization", "Bearer " + accessToken);
            }
            return b;
        }

        ResultActions call(HttpMethod method, String uri) throws Exception {
            return mockMvc.perform(signed(method, uri, new byte[0], false));
        }

        ResultActions refresh() throws Exception {
            byte[] body = json(Map.of("refresh_token", refreshToken));
            String saved = accessToken;
            accessToken = null; // refresh is not authenticated by the access token
            ResultActions r = mockMvc.perform(signed(HttpMethod.POST, "/api/auth/refresh", body, false));
            accessToken = saved;
            return r;
        }
    }

    // ------------------------------------------------------------------------------------------------ tests

    @Test
    void deviceLoginReturnsBothTokensAndSignedRequestsWork() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone
            .login(user.getLogin())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_type").value("DEVICE"))
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andExpect(jsonPath("$.expires_in").value(900));

        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());

        // same token without the signature headers: refused
        mockMvc
            .perform(get("/api/account").header("Authorization", "Bearer " + phone.accessToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.device_signature_missing"));
    }

    @Test
    void aCopiedTokenIsUselessOnAnotherDevice() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());

        // attacker copies access + refresh tokens and even the device id, but has its own key
        Phone attacker = new Phone();
        attacker.accessToken = phone.accessToken;
        attacker.refreshToken = phone.refreshToken;
        MockHttpServletRequestBuilder forged = attacker.signed(HttpMethod.GET, "/api/account", new byte[0], false);
        mockMvc.perform(forged).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.device_mismatch"));

        // same device id header, wrong key
        String ts = String.valueOf(System.currentTimeMillis());
        String canonical = CanonicalRequest.forRequest("GET", "/api/account", ts, "nonce-abcdefgh", phone.deviceId, CanonicalRequest.bodyHash(new byte[0]));
        mockMvc
            .perform(
                get("/api/account")
                    .header("Authorization", "Bearer " + phone.accessToken)
                    .header("X-Device-Id", phone.deviceId)
                    .header("X-Timestamp", ts)
                    .header("X-Nonce", "nonce-abcdefgh")
                    .header("X-Signature", attacker.deviceKey.sign(canonical))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.device_signature_invalid"));

        // refresh with the stolen refresh token, signed with the attacker's key: refused, and the real session survives
        attacker.accessToken = null;
        byte[] body = json(Map.of("refresh_token", phone.refreshToken));
        String ts2 = String.valueOf(System.currentTimeMillis());
        String canonical2 = CanonicalRequest.forRequest("POST", "/api/auth/refresh", ts2, "nonce-ijklmnop", phone.deviceId, CanonicalRequest.bodyHash(body));
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-Device-Id", phone.deviceId)
                    .header("X-Timestamp", ts2)
                    .header("X-Nonce", "nonce-ijklmnop")
                    .header("X-Signature", attacker.deviceKey.sign(canonical2))
            )
            .andExpect(status().isUnauthorized());
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());
        phone.refresh().andExpect(status().isOk());
    }

    @Test
    void replayedRequestIsRefused() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());
        MockHttpServletRequestBuilder once = phone.signed(HttpMethod.GET, "/api/account", new byte[0], false);
        mockMvc.perform(once).andExpect(status().isOk());
        mockMvc.perform(once).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.device_nonce_reused"));
    }

    @Test
    void loginChallengeIsSingleUseAndBoundToTheSignature() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        String nonce = challenge();
        String sig = phone.deviceKey.sign(CanonicalRequest.forLogin(nonce, phone.deviceId, user.getLogin()));

        // signature over another username is refused (and consumes the challenge)
        phone
            .login(user.getLogin(), nonce, phone.deviceKey.sign(CanonicalRequest.forLogin(nonce, phone.deviceId, "someone-else")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.device_signature_invalid"));
        phone.login(user.getLogin(), nonce, sig).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.challenge_invalid"));
        phone.login(user.getLogin(), "never-issued-nonce", sig).andExpect(status().isUnauthorized());
        phone.login(user.getLogin()).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesAndReusingAnOldRefreshTokenKillsTheSession() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());
        String first = phone.refreshToken;

        String rotated = phone.refresh().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        phone.take(rotated);
        assertThat(phone.refreshToken).isNotEqualTo(first);
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());

        // the old token again, within the grace period: parallel request, no revocation
        String current = phone.refreshToken;
        phone.refreshToken = first;
        phone.refresh().andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("error.refresh_in_progress"));
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());

        // after the grace period: theft → the whole session is revoked, including the current access token
        applicationProperties.getSecurity().getSessions().setRefreshReuseGraceSeconds(0);
        Thread.sleep(1100);
        phone.refresh().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.refresh_token_reused"));
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.session_revoked"));
        phone.refreshToken = current;
        phone.refresh().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.session_revoked"));
    }

    @Test
    void logoutEndsTheSessionImmediately() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());
        phone.call(HttpMethod.POST, "/api/auth/logout").andExpect(status().isNoContent());
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("error.session_revoked"));
        phone.refresh().andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsListAndLogOutAnotherDevice() throws Exception {
        User user = user(false);
        Phone a = new Phone();
        Phone b = new Phone();
        a.login(user.getLogin()).andExpect(status().isOk());
        b.login(user.getLogin()).andExpect(status().isOk());

        a.call(HttpMethod.GET, "/api/account/sessions").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        a.call(HttpMethod.DELETE, "/api/account/sessions/" + b.sid).andExpect(status().isNoContent());
        b.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized());
        a.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());

        // logging in again on the same phone replaces that phone's previous session
        String oldSid = a.sid;
        String oldAccess = a.accessToken;
        a.login(user.getLogin()).andExpect(status().isOk());
        assertThat(a.sid).isNotEqualTo(oldSid);
        String newAccess = a.accessToken;
        a.accessToken = oldAccess;
        a.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized());
        a.accessToken = newAccess;
        a.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());
    }

    @Test
    void passwordChangeLogsOutTheOtherDevices() throws Exception {
        User user = user(false);
        Phone a = new Phone();
        Phone b = new Phone();
        a.login(user.getLogin()).andExpect(status().isOk());
        b.login(user.getLogin()).andExpect(status().isOk());

        byte[] body = json(Map.of("currentPassword", PASSWORD, "newPassword", "An0ther-Passw0rd"));
        mockMvc.perform(a.signed(HttpMethod.POST, "/api/account/change-password", body, false)).andExpect(status().isOk());

        a.call(HttpMethod.GET, "/api/account").andExpect(status().isOk());
        b.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized());
    }

    @Test
    void pinChangeNeedsTheBiometricTransactionKey() throws Exception {
        User user = user(false);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());
        byte[] body = json(Map.of("password", PASSWORD, "pin", "4826"));

        mockMvc
            .perform(phone.signed(HttpMethod.PUT, "/api/plus/security/pin", body, false))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.transaction_signature_invalid"));
        mockMvc.perform(phone.signed(HttpMethod.PUT, "/api/plus/security/pin", body, true)).andExpect(status().isNoContent());
    }

    @Test
    void blockedUserAndBlockedDeviceAreLockedOut() throws Exception {
        User user = user(false);
        User admin = user(true);
        Phone phone = new Phone();
        phone.login(user.getLogin()).andExpect(status().isOk());
        String adminToken = adminLogin(admin.getLogin());

        mockMvc
            .perform(
                post("/api/admin/security/users/" + user.getLogin() + "/block")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"fraud investigation\"}")
            )
            .andExpect(status().isNoContent());
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized());
        phone.refresh().andExpect(status().isUnauthorized());
        phone.login(user.getLogin()).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.account_blocked"));

        mockMvc
            .perform(delete("/api/admin/security/users/" + user.getLogin() + "/block").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
        phone.login(user.getLogin()).andExpect(status().isOk());

        mockMvc
            .perform(post("/api/admin/security/devices/" + phone.deviceId + "/block").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
        phone.call(HttpMethod.GET, "/api/account").andExpect(status().isUnauthorized());
        phone.login(user.getLogin()).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.device_blocked"));
    }

    @Test
    void adminWithoutDeviceKeyGetsAShortSessionWithoutRefreshToken() throws Exception {
        User admin = user(true);
        String body = mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("username", admin.getLogin(), "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_type").value("ADMIN"))
            .andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String token = objectMapper.readTree(body).get("access_token").asText();
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void legacyLoginWorksInOptionalModeAndIsRefusedInEnforceMode() throws Exception {
        User user = user(false);
        byte[] login = json(Map.of("username", user.getLogin(), "password", PASSWORD));
        String body = mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_type").value("LEGACY"))
            .andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String legacyToken = objectMapper.readTree(body).get("id_token").asText();
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + legacyToken)).andExpect(status().isOk());

        applicationProperties.getSecurity().getSessions().setDeviceBinding(DeviceBindingMode.ENFORCE);
        mockMvc
            .perform(get("/api/account").header("Authorization", "Bearer " + legacyToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.device_binding_required"));
        mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.device_binding_required"));
        // the new app still works in enforce mode
        new Phone().login(user.getLogin()).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String challenge() throws Exception {
        String body = mockMvc.perform(get("/api/auth/challenge")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("nonce").asText();
    }

    private String adminLogin(String login) throws Exception {
        String body = mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("username", login, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("access_token").asText();
    }

    private byte[] json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o).getBytes(StandardCharsets.UTF_8);
    }

    private User user(boolean admin) {
        User created = new TransactionTemplate(transactionManager).execute(s -> {
            String login = "it-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
            User u = new User();
            u.setLogin(login);
            u.setEmail(login);
            u.setPassword(passwordEncoder.encode(PASSWORD));
            u.setActivated(true);
            u.setFirstName("Device");
            u.setLastName("Tester");
            u.setLangKey("en");
            u.getAuthorities().add(authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow());
            if (admin) {
                u.getAuthorities().add(authorityRepository.findById(AuthoritiesConstants.ADMIN).orElseThrow());
            }
            return userRepository.saveAndFlush(u);
        });
        createdUserIds.add(created.getId());
        return created;
    }

    @AfterEach
    void cleanUp() {
        var sessions = applicationProperties.getSecurity().getSessions();
        sessions.setDeviceBinding(DeviceBindingMode.OPTIONAL);
        sessions.setRefreshReuseGraceSeconds(30);
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            for (String deviceId : deviceIds) {
                jdbcTemplate.update("delete from access_block where block_type = 'DEVICE' and block_value = ?", deviceId);
            }
            for (Long userId : createdUserIds) {
                jdbcTemplate.update("delete from refresh_token where session_id in (select id from user_session where user_id = ?)", userId);
                jdbcTemplate.update("delete from user_session where user_id = ?", userId);
                jdbcTemplate.update("delete from access_block where block_type = 'USER' and block_value = (select login from jhi_user where id = ?)", userId);
                jdbcTemplate.update("delete from transaction_pin where user_id = ?", userId);
                jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
                jdbcTemplate.update("delete from jhi_user where id = ?", userId);
            }
        });
        createdUserIds.clear();
        deviceIds.clear();
    }
}
