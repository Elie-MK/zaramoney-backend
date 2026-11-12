package com.zekodnix.zaramoney.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.SystemAccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin deposit (TODO.md Phase 3b): validation, idempotency, row locking, balanced ledger against the SYSTEM
 * settlement account. Not @Transactional: the concurrency tests need committed data (same pattern as TransactionFlowIT).
 */
@IntegrationTest
@AutoConfigureMockMvc
class BackOfficeDepositIT {

    private static final String DEPOSIT = "/api/admin/backoffice/user/deposit";

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private BigDecimal settlementBalanceBefore;
    private User admin;

    record Customer(User user, BankAccount account) {}

    @BeforeEach
    void setUp() {
        settlementBalanceBefore = settlementBalance();
        admin = newUser("it-admin-");
    }

    // ------------------------------------------------------------------------------------------ tests

    @Test
    void depositCreditsTheCustomerAndDebitsTheSettlementAccountWithABalancedLedger() throws Exception {
        Customer alice = customer("10.00", AccountStatus.ACTIVE);

        MvcResult result = deposit(alice, "25.50", key()).andExpect(status().isOk()).andReturn();
        String reference = objectMapper.readTree(result.getResponse().getContentAsString()).get("transactionReference").asText();

        assertThat(balance(alice)).isEqualByComparingTo("35.50");
        assertThat(settlementBalance()).isEqualByComparingTo(settlementBalanceBefore.subtract(new BigDecimal("25.50")));

        // Ledger: exactly one DEBIT (settlement) and one CREDIT (customer) of the same amount
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
            "select l.entry_type, l.amount, a.account_number from ledger_entry l " +
            "join bank_account a on a.id = l.account_id join transaction_record t on t.id = l.transaction_id " +
            "where t.transaction_reference = ? order by l.entry_type",
            reference
        );
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("entry_type")).isEqualTo("CREDIT");
        assertThat(entries.get(0).get("account_number")).isEqualTo(alice.account().getAccountNumber());
        assertThat((BigDecimal) entries.get(0).get("amount")).isEqualByComparingTo("25.50");
        assertThat(entries.get(1).get("entry_type")).isEqualTo("DEBIT");
        assertThat(entries.get(1).get("account_number")).isEqualTo(SystemAccountService.SETTLEMENT_ACCOUNT_NUMBER);
        assertThat((BigDecimal) entries.get(1).get("amount")).isEqualByComparingTo("25.50");

        // The admin who made the deposit is recorded with the transaction reference
        Long recordedAdmin = jdbcTemplate.queryForObject(
            "select user_id from idempotency_record where transaction_reference = ?",
            Long.class,
            reference
        );
        assertThat(recordedAdmin).isEqualTo(admin.getId());

        // The customer sees a received DEPOSIT from ZaraMoney, never the settlement account
        mockMvc
            .perform(get("/api/plus/transaction-records").with(user(alice.user().getLogin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].transactionType").value("DEPOSIT"))
            .andExpect(jsonPath("$.content[0].direction").value("RECEIVED"))
            .andExpect(jsonPath("$.content[0].counterpartyName").value("ZaraMoney"));
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalDepositAndCreditsOnce() throws Exception {
        Customer alice = customer("0.00", AccountStatus.ACTIVE);
        String key = key();

        String first = deposit(alice, "40", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String retry = deposit(alice, "40", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ref(retry)).isEqualTo(ref(first));
        assertThat(balance(alice)).isEqualByComparingTo("40.00");

        // same key, different amount -> 409, nothing credited
        deposit(alice, "41", key).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("error.idempotencykeyreused"));
        assertThat(balance(alice)).isEqualByComparingTo("40.00");
    }

    @Test
    void parallelDoubleClickCreditsOnlyOnce() throws Exception {
        Customer alice = customer("0.00", AccountStatus.ACTIVE);
        String key = key();

        List<Integer> statuses = runInParallel(4, () -> deposit(alice, "30", key).andReturn().getResponse().getStatus());

        // the first one wins; the others either replay it (200) or hit the reservation (409), never credit again
        assertThat(statuses).allMatch(s -> s == 200 || s == 409).contains(200);
        assertThat(balance(alice)).isEqualByComparingTo("30.00");
        assertThat(depositCount(alice)).isEqualTo(1);
    }

    @Test
    void parallelDepositsWithDifferentKeysAreAllApplied() throws Exception {
        Customer alice = customer("0.00", AccountStatus.ACTIVE);

        List<Integer> statuses = runInParallel(5, () -> deposit(alice, "10", key()).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(balance(alice)).isEqualByComparingTo("50.00"); // no lost update
        assertThat(settlementBalance()).isEqualByComparingTo(settlementBalanceBefore.subtract(new BigDecimal("50.00")));
    }

    @Test
    void invalidAmountsAndMissingKeyAreRejected() throws Exception {
        Customer alice = customer("10.00", AccountStatus.ACTIVE);

        for (String amount : List.of("0", "-5", "0.001", "12.345")) {
            deposit(alice, amount, key()).andExpect(status().isBadRequest());
        }
        deposit(alice, "10", null).andExpect(status().isBadRequest());
        deposit(alice, "10", "short").andExpect(status().isBadRequest());
        assertThat(balance(alice)).isEqualByComparingTo("10.00");
    }

    @Test
    void unknownOrWrongTargetsReturn4xxAndCreditNothing() throws Exception {
        Customer alice = customer("10.00", AccountStatus.ACTIVE);
        Customer bob = customer("10.00", AccountStatus.ACTIVE);
        Customer closed = customer("10.00", AccountStatus.CLOSED);
        String aliceAccount = alice.account().getAccountNumber();

        deposit(aliceAccount, "nobody-" + UUID.randomUUID() + "@zaramoney.test", "10", key())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.usernotfound"));
        deposit("251299999999", alice.user().getLogin(), "10", key())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.accountnotfound"));
        deposit(SystemAccountService.PROMO_ACCOUNT_NUMBER, alice.user().getLogin(), "10", key())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.accountnotfound"));
        deposit(aliceAccount, bob.user().getLogin(), "10", key())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.accountusermismatch"));
        deposit(closed, "10", key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.accountnotactive"));

        assertThat(balance(alice)).isEqualByComparingTo("10.00");
        assertThat(balance(closed)).isEqualByComparingTo("10.00");
        assertThat(settlementBalance()).isEqualByComparingTo(settlementBalanceBefore);
    }

    @Test
    void emailIsMatchedCaseInsensitively() throws Exception {
        Customer alice = customer("0.00", AccountStatus.ACTIVE);
        deposit(alice.account().getAccountNumber(), "  " + alice.user().getLogin().toUpperCase() + " ", "5", key()).andExpect(status().isOk());
        assertThat(balance(alice)).isEqualByComparingTo("5.00");
    }

    @Test
    void customersCannotDeposit() throws Exception {
        Customer alice = customer("10.00", AccountStatus.ACTIVE);
        mockMvc
            .perform(
                post(DEPOSIT)
                    .with(user(alice.user().getLogin()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(alice.account().getAccountNumber(), alice.user().getLogin(), "10", key()))
            )
            .andExpect(status().isForbidden());
        assertThat(balance(alice)).isEqualByComparingTo("10.00");
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions deposit(Customer c, String amount, String key) throws Exception {
        return deposit(c.account().getAccountNumber(), c.user().getLogin(), amount, key);
    }

    private ResultActions deposit(String accountNumber, String email, String amount, String key) throws Exception {
        return mockMvc.perform(post(DEPOSIT).with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body(accountNumber, email, amount, key)));
    }

    private byte[] body(String accountNumber, String email, String amount, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", new BigDecimal(amount));
        body.put("userAccountNumber", accountNumber);
        body.put("userEmail", email);
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        return objectMapper.writeValueAsBytes(body);
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getLogin()).authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private List<Integer> runInParallel(int threads, Callable<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(
                    pool.submit(() -> {
                        start.await();
                        return call.call();
                    })
                );
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String ref(String json) throws Exception {
        return objectMapper.readTree(json).get("transactionReference").asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private BigDecimal balance(Customer c) {
        return bankAccountRepository.findById(c.account().getId()).orElseThrow().getBalance();
    }

    private BigDecimal settlementBalance() {
        return bankAccountRepository.findByAccountNumber(SystemAccountService.SETTLEMENT_ACCOUNT_NUMBER).orElseThrow().getBalance();
    }

    private Integer depositCount(Customer c) {
        return jdbcTemplate.queryForObject(
            "select count(*) from transaction_record where transaction_type = 'DEPOSIT' and receiver_id = ?",
            Integer.class,
            c.account().getId()
        );
    }

    private User newUser(String prefix) {
        User created = new TransactionTemplate(transactionManager).execute(s -> {
            String login = prefix + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
            User user = new User();
            user.setLogin(login);
            user.setEmail(login);
            user.setPassword("$2a$10$" + "x".repeat(53));
            user.setActivated(true);
            user.setFirstName("First");
            user.setLastName("Last");
            user.setLangKey("en");
            return userRepository.saveAndFlush(user);
        });
        createdUserIds.add(created.getId());
        return created;
    }

    private Customer customer(String balance, AccountStatus accountStatus) {
        User user = newUser("it-dep-");
        return new TransactionTemplate(transactionManager).execute(s -> {
            BankAccount account = bankAccountRepository.saveAndFlush(
                new BankAccount()
                    .accountNumber("2512" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)))
                    .balance(new BigDecimal(balance))
                    .currency(Currency.USD)
                    .status(accountStatus)
                    .accountType(BankAccountType.CUSTOMER)
                    .createdAt(Instant.now())
                    .user(user)
            );
            userDetailsAccountRepository.saveAndFlush(
                new UserDetailsAccount()
                    .phoneNumber("+243000000000")
                    .facePicture("kyc/face.png")
                    .idCardPicture("kyc/id.png")
                    .country("CD")
                    .address("Kinshasa")
                    .isAgent(false)
                    .kycStatus(KycStatus.VERIFIED)
                    .user(user)
            );
            return new Customer(user, account);
        });
    }

    @AfterEach
    void cleanUp() {
        // The test pool uses auto-commit=false: run the deletes in a transaction or they are rolled back
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createdUserIds.forEach(this::deleteUserData);
            // put the settlement account back where it was, so other tests see a stable balance
            jdbcTemplate.update(
                "update bank_account set balance = ? where account_number = ?",
                settlementBalanceBefore,
                SystemAccountService.SETTLEMENT_ACCOUNT_NUMBER
            );
        });
        createdUserIds.clear();
    }

    private void deleteUserData(Long userId) {
        String accounts = "(select id from bank_account where user_id = " + userId + ")";
        String txs = "(select id from transaction_record where sender_id in " + accounts + " or receiver_id in " + accounts + ")";
        jdbcTemplate.update("delete from notification where user_id = ? or transaction_id in " + txs, userId);
        jdbcTemplate.execute(MoneyTestData.LEDGER_MAINTENANCE);
        jdbcTemplate.update("delete from ledger_entry where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_fee where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_record where id in " + txs);
        jdbcTemplate.update("delete from idempotency_record where user_id = ?", userId);
        jdbcTemplate.update("delete from user_details_account where user_id = ?", userId);
        jdbcTemplate.update("delete from bank_account where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user where id = ?", userId);
    }
}
