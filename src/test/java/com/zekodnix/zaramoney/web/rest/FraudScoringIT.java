package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Fraud.Mode;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

/** Fraud scoring in the real payment flow: stored score/status/reasons, monitor vs enforce, history, admin review queue. */
@IntegrationTest
@AutoConfigureMockMvc
class FraudScoringIT {

    private static final String SEND = "/api/plus/transaction-records/send";
    private static final String QUEUE = "/api/admin/backoffice/fraud/transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationProperties applicationProperties;

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
    private Mode modeBefore;

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
        modeBefore = applicationProperties.getFraud().getMode();
    }

    @AfterEach
    void tearDown() {
        applicationProperties.getFraud().setMode(modeBefore);
        data.cleanUp();
    }

    @Test
    void anOrdinaryPaymentIsScoredClean() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        String ref = ref(send(alice, bob, "10", key()).andExpect(status().isOk()));

        Map<String, Object> row = row(ref);
        assertThat(row).containsEntry("fraud_status", "CLEAN").containsEntry("risk_score", 0).containsEntry("transaction_status", "COMPLETED");
        assertThat(row.get("fraud_reasons")).isNull();
    }

    @Test
    void aSuspiciousPaymentGoesThroughAndIsQueuedForReview() throws Exception {
        // created just now (NEW_ACCOUNT 25), PIN set just now (PIN_CHANGED 20), sends ~95 % of its balance (DRAIN 20) = 65
        Customer alice = data.customer(spec().balance("160.00"));
        Customer bob = data.customer(spec());
        String ref = ref(send(alice, bob, "150", key()).andExpect(status().isOk()));

        assertThat(row(ref)).containsEntry("fraud_status", "SUSPICIOUS").containsEntry("risk_score", 65).containsEntry("fraud_reasons", "DRAIN,NEW_ACCOUNT,PIN_CHANGED");
        assertThat(data.balance(bob)).isEqualByComparingTo("250.00"); // allowed: money moved

        Long id = id(ref);
        mockMvc
            .perform(get(QUEUE + "?size=200").with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.transactionReference == '" + ref + "')].fraudReasons").value("DRAIN,NEW_ACCOUNT,PIN_CHANGED"))
            .andExpect(jsonPath("$[?(@.transactionReference == '" + ref + "')].senderLogin").value(alice.login()));

        mockMvc
            .perform(
                post(QUEUE + "/" + id + "/review").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"CLEARED\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewOutcome").value("CLEARED"))
            .andExpect(jsonPath("$.reviewedBy").value("it-fraud-admin"));
        // reviewed cases leave the default (unreviewed) queue but stay visible with unreviewed=false
        assertThat(queueBody("")).doesNotContain(ref);
        assertThat(queueBody("&unreviewed=false")).contains(ref);
    }

    @Test
    void monitorModeLetsAHardRuleThroughButMarksItBlocked() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        for (int i = 0; i < 15; i++) {
            send(alice, bob, "1", key()).andExpect(status().isOk());
        }
        String ref = ref(send(alice, bob, "1", key()).andExpect(status().isOk())); // 16th in the hour

        assertThat(row(ref)).containsEntry("fraud_status", "BLOCKED").containsEntry("transaction_status", "COMPLETED");
        assertThat((String) row(ref).get("fraud_reasons")).contains("HOURLY_LIMIT:16/PT1H", "BURST:16/PT10M");
        assertThat(data.balance(alice)).isEqualByComparingTo("84.00");
    }

    @Test
    void enforceModeRejectsAndKeepsTheAttemptOnlyForAdmins() throws Exception {
        applicationProperties.getFraud().setMode(Mode.ENFORCE);
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        for (int i = 0; i < 15; i++) {
            send(alice, bob, "1", key()).andExpect(status().isOk());
        }
        String blockedKey = key();
        send(alice, bob, "1", blockedKey).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.transactionblocked"));

        // nothing moved, no ledger
        assertThat(data.balance(alice)).isEqualByComparingTo("85.00");
        assertThat(data.balance(bob)).isEqualByComparingTo("115.00");
        Map<String, Object> attempt = jdbcTemplate.queryForMap(
            "select id, transaction_reference, transaction_status, fraud_status, risk_score from transaction_record " +
            "where sender_id = ? and transaction_status = 'FAILED'",
            alice.account().getId()
        );
        assertThat(attempt).containsEntry("transaction_status", "FAILED").containsEntry("fraud_status", "BLOCKED");
        assertThat(
            jdbcTemplate.queryForObject("select count(*) from ledger_entry where transaction_id = ?", Integer.class, attempt.get("id"))
        ).isZero();

        // neither side sees the blocked attempt; admins do
        String ref = (String) attempt.get("transaction_reference");
        for (Customer c : new Customer[] { alice, bob }) {
            assertThat(
                mockMvc
                    .perform(get("/api/plus/transaction-records?size=100").with(user(c.login())))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString()
            ).doesNotContain(ref);
            mockMvc.perform(get("/api/plus/transaction-records/" + attempt.get("id")).with(user(c.login()))).andExpect(status().isNotFound());
        }
        assertThat(queueBody("&status=BLOCKED")).contains(ref);

        // the idempotency key was released: a retry is scored again (and blocked again), not "in progress"
        send(alice, bob, "1", blockedKey).andExpect(status().isForbidden());
    }

    @Test
    void reviewEndpointsAreAdminOnlyAndOnlyForFlaggedTransactions() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Long clean = id(ref(send(alice, bob, "10", key()).andExpect(status().isOk())));

        mockMvc.perform(get(QUEUE).with(user(alice.login()))).andExpect(status().isForbidden());
        mockMvc
            .perform(post(QUEUE + "/" + clean + "/review").with(user(alice.login())).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"CLEARED\"}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post(QUEUE + "/" + clean + "/review").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"CLEARED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.notflagged"));
        mockMvc
            .perform(post(QUEUE + "/" + Long.MAX_VALUE + "/review").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"CLEARED\"}"))
            .andExpect(status().isNotFound());
        mockMvc
            .perform(post(QUEUE + "/" + clean + "/review").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"MAYBE\"}"))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions send(Customer from, Customer to, String amount, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", key);
        body.put("pin", MoneyTestData.PIN);
        return mockMvc.perform(post(SEND).with(user(from.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)));
    }

    private String queueBody(String params) throws Exception {
        return mockMvc.perform(get(QUEUE + "?size=500" + params).with(asAdmin())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> row(String reference) {
        return jdbcTemplate.queryForMap(
            "select transaction_status, fraud_status, risk_score, fraud_reasons from transaction_record where transaction_reference = ?",
            reference
        );
    }

    private Long id(String reference) {
        return jdbcTemplate.queryForObject("select id from transaction_record where transaction_reference = ?", Long.class, reference);
    }

    private String ref(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("transactionReference").asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private RequestPostProcessor asAdmin() {
        return user("it-fraud-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
