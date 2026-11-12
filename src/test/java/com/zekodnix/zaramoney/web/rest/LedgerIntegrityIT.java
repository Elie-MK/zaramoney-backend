package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.LedgerReconciliationService;
import com.zekodnix.zaramoney.service.SystemAccountService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Phase 4: ledger currency, balance_after, append-only ledger, reconciliation. */
@IntegrationTest
@AutoConfigureMockMvc
class LedgerIntegrityIT {

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

    @Autowired
    private LedgerReconciliationService reconciliationService;

    @Autowired
    private MeterRegistry meterRegistry;

    private MoneyTestData data;
    private TransactionTemplate tx;
    private BigDecimal feeBalanceBefore;

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
        feeBalanceBefore = data.systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER);
    }

    @AfterEach
    void tearDown() {
        data.cleanUp();
        tx.executeWithoutResult(s ->
            jdbcTemplate.update(
                "update bank_account set balance = ? where account_number = ?",
                feeBalanceBefore,
                SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER
            )
        );
    }

    // ------------------------------------------------------------------------------------------ balance_after, currency

    @Test
    void everyEntryRecordsTheBalanceAfterItAndTheAccountCurrency() throws Exception {
        Customer alice = data.customer(spec().balance("1000.00"));
        Customer bob = data.customer(spec());
        String ref = send(alice, bob, "500"); // fee 4.75

        Map<String, Map<String, Object>> rows = ledger(ref);
        assertThat(rows.get(alice.accountNumber())).containsEntry("entry_type", "DEBIT").containsEntry("currency", "USD");
        assertThat((BigDecimal) rows.get(alice.accountNumber()).get("balance_after")).isEqualByComparingTo("495.25");
        assertThat((BigDecimal) rows.get(bob.accountNumber()).get("balance_after")).isEqualByComparingTo("600.00");
        assertThat((BigDecimal) rows.get(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER).get("balance_after")).isEqualByComparingTo(
            data.systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER)
        );
        assertThat(data.balance(alice)).isEqualByComparingTo("495.25");
    }

    @Test
    void entriesUseTheAccountCurrencyNotHardCodedUsd() throws Exception {
        Customer alice = data.customer(spec().currency(Currency.TND));
        Customer bob = data.customer(spec().currency(Currency.TND));
        String ref = send(alice, bob, "10");

        assertThat(ledger(ref).values()).extracting(r -> r.get("currency")).containsOnly("TND");
    }

    // ------------------------------------------------------------------------------------------ append-only

    @Test
    void ledgerEntriesCannotBeUpdatedDeletedOrTruncated() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        String ref = send(alice, bob, "10");
        Long entryId = jdbcTemplate.queryForObject(
            "select min(l.id) from ledger_entry l join transaction_record t on t.id = l.transaction_id where t.transaction_reference = ?",
            Long.class,
            ref
        );

        for (String sql : List.of(
            "update ledger_entry set amount = 0 where id = " + entryId,
            "delete from ledger_entry where id = " + entryId,
            "truncate ledger_entry"
        )) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbcTemplate.execute(sql)))
                .as(sql)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        }
        assertThat(ledger(ref)).hasSize(2);

        // the explicit maintenance switch (SET LOCAL, this transaction only) still allows it; rolled back here
        tx.executeWithoutResult(s -> {
            jdbcTemplate.execute(MoneyTestData.LEDGER_MAINTENANCE);
            assertThat(jdbcTemplate.update("update ledger_entry set amount = amount where id = ?", entryId)).isEqualTo(1);
            s.setRollbackOnly();
        });
        // ... and does not leak into the next transaction
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbcTemplate.update("delete from ledger_entry where id = ?", entryId)))
            .isInstanceOf(DataAccessException.class);
    }

    // ------------------------------------------------------------------------------------------ reconciliation

    @Test
    void cleanTransfersReconcile() throws Exception {
        Customer alice = data.customer(spec().balance("0.00"));
        Customer bob = data.customer(spec().balance("0.00"));
        Customer carol = data.customer(spec().balance("1000.00"));
        send(carol, alice, "200");
        send(alice, bob, "50");

        ReconciliationReportDTO report = reconciliationService.reconcile();

        assertThat(mentions(report, alice.accountNumber())).isFalse();
        assertThat(mentions(report, bob.accountNumber())).isFalse();
        assertThat(mentions(report, carol.accountNumber())).isFalse();
        assertThat(report.balanceTrackingSince()).isNotNull();
        assertThat(meterRegistry.get("zaramoney.ledger.reconciliation.issues").gauge().value()).isEqualTo(report.issueCount());
    }

    @Test
    void aBalanceChangedOutsideTheLedgerIsReportedAsDrift() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        send(alice, bob, "10");
        tx.executeWithoutResult(s ->
            jdbcTemplate.update("update bank_account set balance = balance + 1000 where id = ?", bob.account().getId())
        );

        ReconciliationReportDTO report = reconciliationService.reconcile();

        assertThat(report.ok()).isFalse();
        assertThat(report.balanceDrifts()).anySatisfy(d -> {
            assertThat(d.accountNumber()).isEqualTo(bob.accountNumber());
            assertThat(d.balance()).isEqualByComparingTo("1110.00");
            assertThat(d.lastBalanceAfter()).isEqualByComparingTo("110.00");
        });
        assertThat(meterRegistry.get("zaramoney.ledger.reconciliation.issues").gauge().value()).isGreaterThan(0);
    }

    @Test
    void unbalancedTransactionsChainBreaksAndMissingLedgerAreReported() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        send(alice, bob, "10"); // bob: balance_after 110.00
        String unbalanced = "ZMCT-UNBAL-" + UUID.randomUUID().toString().substring(0, 8);
        String noLedger = "ZMCT-NOLEDGER-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(s -> {
            Long txId = insertTransaction(unbalanced, alice, bob);
            // one CREDIT only, and its balance_after does not follow from the previous one (110 + 5 != 200)
            jdbcTemplate.update(
                "insert into ledger_entry (id, amount, currency, entry_type, created_at, account_id, transaction_id, balance_after) " +
                "values (nextval('sequence_generator'), 5, 'USD', 'CREDIT', now(), ?, ?, 200)",
                bob.account().getId(),
                txId
            );
            insertTransaction(noLedger, alice, bob);
        });

        ReconciliationReportDTO report = reconciliationService.reconcile();

        assertThat(report.ok()).isFalse();
        assertThat(report.unbalancedTransactions()).anySatisfy(u -> {
            assertThat(u.transactionReference()).isEqualTo(unbalanced);
            assertThat(u.debits()).isEqualByComparingTo("0");
            assertThat(u.credits()).isEqualByComparingTo("5");
        });
        assertThat(report.chainBreaks()).anySatisfy(b -> {
            assertThat(b.accountNumber()).isEqualTo(bob.accountNumber());
            assertThat(b.previousBalanceAfter()).isEqualByComparingTo("110.00");
            assertThat(b.balanceAfter()).isEqualByComparingTo("200.00");
        });
        assertThat(report.completedTransactionsWithoutLedger()).contains(noLedger);
    }

    @Test
    void reconciliationEndpointIsAdminOnly() throws Exception {
        String url = "/api/admin/backoffice/reports/reconciliation";
        mockMvc
            .perform(get(url).with(user("it-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").isBoolean())
            .andExpect(jsonPath("$.issueCount").isNumber())
            .andExpect(jsonPath("$.legacyUnledgeredTotal").isNumber());
        mockMvc.perform(get(url).with(user("it-user"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ helpers

    private String send(Customer from, Customer to, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", MoneyTestData.PIN);
        String json = mockMvc
            .perform(
                post("/api/plus/transaction-records/send")
                    .with(user(from.login()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(json).get("transactionReference").asText();
    }

    /** account number → row (entry_type, currency, amount, balance_after) for one transaction. */
    private Map<String, Map<String, Object>> ledger(String reference) {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        jdbcTemplate
            .queryForList(
                "select a.account_number, l.entry_type, l.currency, l.amount, l.balance_after from ledger_entry l " +
                "join bank_account a on a.id = l.account_id join transaction_record t on t.id = l.transaction_id " +
                "where t.transaction_reference = ? order by l.id",
                reference
            )
            .forEach(r -> rows.put((String) r.get("account_number"), r));
        return rows;
    }

    private Long insertTransaction(String reference, Customer sender, Customer receiver) {
        Long id = jdbcTemplate.queryForObject("select nextval('sequence_generator')", Long.class);
        jdbcTemplate.update(
            "insert into transaction_record (id, transaction_type, send_amount, receive_amount, exchange_rate, fee, currency_send_amount, " +
            "currency_receive_amount, transaction_status, transaction_reference, risk_score, fraud_status, created_at, transaction_date, " +
            "sender_id, receiver_id) values (?, 'TRANSFER', 5, 5, 1, 0, 'USD', 'USD', 'COMPLETED', ?, 0, 'CLEAN', now(), now(), ?, ?)",
            id,
            reference,
            sender.account().getId(),
            receiver.account().getId()
        );
        return id;
    }

    private static boolean mentions(ReconciliationReportDTO report, String accountNumber) {
        return (
            report.balanceDrifts().stream().anyMatch(d -> d.accountNumber().equals(accountNumber)) ||
            report.chainBreaks().stream().anyMatch(b -> b.accountNumber().equals(accountNumber))
        );
    }
}
