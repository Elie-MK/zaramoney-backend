package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistent audit trail: what is recorded, by whom, that failures leave no trace, append-only, admin query. */
@IntegrationTest
@AutoConfigureMockMvc
class AuditLogIT {

    private static final String AUDIT = "/api/admin/backoffice/audit";

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
    private TransactionTemplate tx;
    private User admin;

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
        tx = new TransactionTemplate(transactionManager);
        admin = data.userWithoutAccount();
        tx.executeWithoutResult(s ->
            jdbcTemplate.update("insert into jhi_user_authority (user_id, authority_name) values (?, ?)", admin.getId(), AuthoritiesConstants.ADMIN)
        );
    }

    @AfterEach
    void tearDown() {
        data.cleanUp();
    }

    @Test
    void depositIsAuditedWithActorTargetDetailsAndIp() throws Exception {
        Customer alice = data.customer(spec());
        String json = mockMvc
            .perform(
                post("/api/admin/backoffice/user/deposit")
                    .with(asAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deposit(alice.accountNumber(), alice.login(), "25"))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String ref = objectMapper.readTree(json).get("transactionReference").asText();

        Map<String, Object> event = single("DEPOSIT", ref);
        assertThat(event).containsEntry("actor_login", admin.getLogin()).containsEntry("target_type", "TRANSACTION").containsEntry("ip", "127.0.0.1");
        assertThat((String) event.get("details")).contains("amount=25.00 USD", "account=" + alice.accountNumber(), "owner=" + alice.login());
    }

    @Test
    void aFailedActionLeavesNoAuditRow() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        // account of alice, e-mail of bob → 400 accountusermismatch, rolled back together with its audit row
        mockMvc
            .perform(
                post("/api/admin/backoffice/user/deposit")
                    .with(asAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deposit(alice.accountNumber(), bob.login(), "25"))
            )
            .andExpect(status().isBadRequest());
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where action = 'DEPOSIT' and details like ?",
                Integer.class,
                "%account=" + alice.accountNumber() + "%"
            )
        ).isZero();
    }

    @Test
    void kycDecisionAndBankAccountChangesAreAudited() throws Exception {
        Customer alice = data.customer(spec().kyc(KycStatus.PENDING));
        Long detailsId = jdbcTemplate.queryForObject("select id from user_details_account where user_id = ?", Long.class, alice.user().getId());
        mockMvc.perform(post("/api/admin/backoffice/kyc/" + detailsId + "/approve").with(asAdmin())).andExpect(status().isOk());
        assertThat(single("KYC_APPROVED", alice.login()).get("details")).isEqualTo("PENDING -> VERIFIED (userDetails " + detailsId + ")");

        mockMvc
            .perform(
                patch("/api/bank-accounts/" + alice.account().getId())
                    .with(asAdmin())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":" + alice.account().getId() + ",\"status\":\"SUSPENDED\",\"balance\":999}")
            )
            .andExpect(status().isOk());
        assertThat(single("BANK_ACCOUNT_UPDATED", alice.accountNumber()).get("details")).isEqualTo("status ACTIVE->SUSPENDED");
    }

    @Test
    void grantingTheAdminRoleIsAudited() throws Exception {
        User bob = data.userWithoutAccount();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", bob.getId());
        body.put("login", bob.getLogin());
        body.put("email", bob.getEmail());
        body.put("activated", true);
        body.put("langKey", "en");
        body.put("authorities", Set.of(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN));
        mockMvc
            .perform(put("/api/admin/users").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().isOk());

        assertThat(single("USER_UPDATED", bob.getLogin()).get("details")).isEqualTo("authorities []->[ROLE_ADMIN, ROLE_USER]");
    }

    @Test
    void customerSecurityChangesAreAudited() throws Exception {
        Customer alice = data.customer(spec()); // PIN set without a logged-in user (system)
        Map<String, Object> pin = single("PIN_SET", alice.login());
        assertThat(pin.get("actor_login")).isNull();
        assertThat(pin.get("details")).isEqualTo("first PIN");

        mockMvc
            .perform(
                post("/api/account/change-password")
                    .with(user(alice.login()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + MoneyTestData.PASSWORD + "\",\"newPassword\":\"An0ther-Str0ng-Pass\"}")
            )
            .andExpect(status().isOk());
        assertThat(single("PASSWORD_CHANGED", alice.login())).containsEntry("actor_login", alice.login());
    }

    @Test
    void theAuditTableIsAppendOnly() throws Exception {
        Customer alice = data.customer(spec());
        Long id = (Long) single("PIN_SET", alice.login()).get("id");
        for (String sql : List.of(
            "update audit_event set details = 'x' where id = " + id,
            "delete from audit_event where id = " + id,
            "truncate audit_event"
        )) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbcTemplate.execute(sql)))
                .as(sql)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        }
    }

    @Test
    void auditQueryFiltersAndIsAdminOnly() throws Exception {
        Customer alice = data.customer(spec());
        mockMvc
            .perform(get(AUDIT + "?action=PIN_SET&targetId=" + alice.login()).with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].action").value("PIN_SET"))
            .andExpect(jsonPath("$[0].targetType").value("USER"));
        mockMvc
            .perform(get(AUDIT + "?targetId=" + alice.login() + "&from=2099-01-01T00:00:00Z").with(asAdmin()))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc
            .perform(get(AUDIT + "?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z").with(asAdmin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invaliddaterange"));
        mockMvc.perform(get(AUDIT).with(user(alice.login()))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** The single audit row for this action and target. */
    private Map<String, Object> single(String action, String targetId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select id, actor_login, target_type, details, ip from audit_event where action = ? and target_id = ?",
            action,
            targetId
        );
        assertThat(rows).as(action + " " + targetId).hasSize(1);
        return rows.get(0);
    }

    private byte[] deposit(String accountNumber, String email, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", new BigDecimal(amount));
        body.put("userAccountNumber", accountNumber);
        body.put("userEmail", email);
        body.put("idempotencyKey", UUID.randomUUID().toString());
        return objectMapper.writeValueAsBytes(body);
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getLogin()).authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
