package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

/** TODO.md #156: an admin freezing (SUSPENDED / CLOSED) a customer account logs its owner out everywhere. */
@IntegrationTest
@AutoConfigureMockMvc
class AccountFreezeLogsOutIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserDetailsAccountRepository userDetailsAccountRepository;

    @Autowired
    private TransactionPinService transactionPinService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MoneyTestData data;

    @BeforeEach
    void setUp() {
        data = new MoneyTestData(
            userRepository,
            bankAccountRepository,
            userDetailsAccountRepository,
            transactionPinService,
            passwordEncoder,
            jdbcTemplate,
            transactionManager
        );
    }

    @AfterEach
    void tearDown() {
        data.cleanUp();
    }

    @Test
    void suspendingTheAccountRevokesEverySessionOfTheOwner() throws Exception {
        Customer alice = data.customer(spec());
        String phone = login(alice);
        String laptop = login(alice);
        account(phone).andExpect(status().isOk());

        patchStatus(alice, "SUSPENDED").andExpect(status().isOk());

        account(phone).andExpect(status().isUnauthorized());
        account(laptop).andExpect(status().isUnauthorized());
        assertThat(revokeReasons(alice)).containsOnly("account_suspended").hasSize(2);
        assertThat(auditDetails(alice)).contains("status ACTIVE->SUSPENDED, owner's sessions revoked (account_suspended)");

        // not a login block: the user can sign in again (payments stay refused: 403 accountnotactive)
        account(login(alice)).andExpect(status().isOk());
    }

    @Test
    void closingThroughAFullUpdateRevokesToo() throws Exception {
        Customer alice = data.customer(spec());
        String token = login(alice);

        Map<String, Object> dto = read(alice);
        dto.put("status", "CLOSED");
        mockMvc
            .perform(
                put("/api/bank-accounts/" + alice.account().getId())
                    .with(asAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(dto))
            )
            .andExpect(status().isOk());

        account(token).andExpect(status().isUnauthorized());
        assertThat(revokeReasons(alice)).containsOnly("account_closed");
    }

    @Test
    void otherChangesAndReactivationKeepTheSessions() throws Exception {
        Customer alice = data.customer(spec().status(com.zekodnix.zaramoney.domain.enumeration.AccountStatus.SUSPENDED));
        String token = login(alice);

        patchStatus(alice, "ACTIVE").andExpect(status().isOk()); // reactivation
        patchStatus(alice, "ACTIVE").andExpect(status().isOk()); // no change
        account(token).andExpect(status().isOk());
        assertThat(revokeReasons(alice)).isEmpty();
    }

    @Test
    void aRejectedUpdateRevokesNothing() throws Exception {
        // funded account: changing its currency is refused (409 accountinuse); the status change in the same
        // request rolls back, and so does the revocation (same transaction)
        Customer alice = data.customer(spec());
        String token = login(alice);

        mockMvc
            .perform(
                patch("/api/bank-accounts/" + alice.account().getId())
                    .with(asAdmin())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + alice.account().getId() + ",\"status\":\"SUSPENDED\",\"currency\":\"TND\"}")
            )
            .andExpect(status().isConflict());

        account(token).andExpect(status().isOk());
        assertThat(revokeReasons(alice)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** Real login (JWT + server-side session), like an old app version without device keys. */
    private String login(Customer c) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", c.login());
        body.put("password", MoneyTestData.PASSWORD);
        String json = mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(json).get("access_token").asText();
    }

    private ResultActions account(String token) throws Exception {
        return mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + token));
    }

    private ResultActions patchStatus(Customer c, String status) throws Exception {
        return mockMvc.perform(
            patch("/api/bank-accounts/" + c.account().getId())
                .with(asAdmin())
                .contentType("application/merge-patch+json")
                .content("{\"id\":" + c.account().getId() + ",\"status\":\"" + status + "\"}")
        );
    }

    private Map<String, Object> read(Customer c) throws Exception {
        String json = mockMvc
            .perform(get("/api/bank-accounts/" + c.account().getId()).with(asAdmin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private List<String> revokeReasons(Customer c) {
        return jdbcTemplate.queryForList(
            "select revoke_reason from user_session where user_id = ? and revoked_at is not null",
            String.class,
            c.user().getId()
        );
    }

    private List<String> auditDetails(Customer c) {
        return jdbcTemplate.queryForList(
            "select details from audit_event where action = 'BANK_ACCOUNT_UPDATED' and target_id = ?",
            String.class,
            c.accountNumber()
        );
    }

    private RequestPostProcessor asAdmin() {
        return user("it-freeze-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
