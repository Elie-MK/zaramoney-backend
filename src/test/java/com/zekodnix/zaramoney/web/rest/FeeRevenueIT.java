package com.zekodnix.zaramoney.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.zekodnix.zaramoney.service.RevenueReportService;
import com.zekodnix.zaramoney.service.SignupBonusService;
import com.zekodnix.zaramoney.service.SystemAccountService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fee revenue through the ledger, explicit sender account, the admin revenue report and the 2026-11 fee backfill.
 * Not @Transactional (the PIN check needs committed data), same pattern as {@link TransactionFlowIT}.
 */
@IntegrationTest
@AutoConfigureMockMvc
class FeeRevenueIT {

    private static final String PASSWORD = "Str0ng-Passw0rd";
    private static final String PIN = "4826";
    private static final String SEND = "/api/plus/transaction-records/send";
    private static final String WITHDRAWAL = "/api/plus/transaction-records/withdrawal";
    private static final String REPORT = "/api/admin/backoffice/reports/revenue";

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
    private SignupBonusService signupBonusService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private BigDecimal feeBalanceBefore;
    private BigDecimal promoBalanceBefore;

    record Customer(User user, BankAccount account) {
        String login() {
            return user.getLogin();
        }

        String accountNumber() {
            return account.getAccountNumber();
        }
    }

    @BeforeEach
    void snapshot() {
        feeBalanceBefore = systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER);
        promoBalanceBefore = systemBalance(SystemAccountService.PROMO_ACCOUNT_NUMBER);
    }

    // ------------------------------------------------------------------------------------------ fee ledger

    @Test
    void transferFeeIsCreditedToTheFeeRevenueAccountWithABalancedLedger() throws Exception {
        Customer alice = customer("2000.00", false);
        Customer bob = customer("10.00", false);

        // 500 → fee 0.95% = 4.75 (stays under the default 1000/day limit)
        String ref = ref(send(alice, bob.accountNumber(), "500", null).andExpect(status().isOk()).andExpect(jsonPath("$.fee").value(4.75)));

        assertThat(balance(alice)).isEqualByComparingTo("1495.25");
        assertThat(balance(bob)).isEqualByComparingTo("510.00");
        assertThat(systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER)).isEqualByComparingTo(feeBalanceBefore.add(new BigDecimal("4.75")));
        assertLedger(
            ref,
            Map.of(
                "DEBIT " + alice.accountNumber(),
                "504.75",
                "CREDIT " + bob.accountNumber(),
                "500.00",
                "CREDIT " + SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER,
                "4.75"
            )
        );
    }

    @Test
    void transferWithoutFeeHasNoFeeEntry() throws Exception {
        Customer alice = customer("100.00", false);
        Customer bob = customer("10.00", false);

        String ref = ref(send(alice, bob.accountNumber(), "50", null).andExpect(status().isOk()));

        assertThat(systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER)).isEqualByComparingTo(feeBalanceBefore);
        assertLedger(ref, Map.of("DEBIT " + alice.accountNumber(), "50.00", "CREDIT " + bob.accountNumber(), "50.00"));
    }

    @Test
    void withdrawalFeeIsCreditedToTheFeeRevenueAccount() throws Exception {
        Customer alice = customer("100.00", false);
        Customer agent = customer("0.00", true);

        // 40 → fee 2.50% = 1.00
        String ref = ref(withdraw(alice, agent.accountNumber(), "40").andExpect(status().isOk()));

        assertThat(balance(alice)).isEqualByComparingTo("59.00");
        assertThat(balance(agent)).isEqualByComparingTo("40.00");
        assertLedger(
            ref,
            Map.of(
                "DEBIT " + alice.accountNumber(),
                "41.00",
                "CREDIT " + agent.accountNumber(),
                "40.00",
                "CREDIT " + SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER,
                "1.00"
            )
        );
    }

    // ------------------------------------------------------------------------------------------ sender account

    @Test
    void userWithSeveralAccountsMustChooseTheSenderAccount() throws Exception {
        Customer alice = customer("100.00", false);
        BankAccount second = extraAccount(alice.user(), "200.00");
        Customer bob = customer("10.00", false);

        send(alice, bob.accountNumber(), "10", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.senderaccountrequired"));
        send(alice, bob.accountNumber(), "10", bob.accountNumber())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.senderaccountnotfound"));

        send(alice, bob.accountNumber(), "10", second.getAccountNumber())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountNumber").value(second.getAccountNumber()));
        assertThat(bankAccountRepository.findById(second.getId()).orElseThrow().getBalance()).isEqualByComparingTo("190.00");
        assertThat(balance(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void senderAccountIsOptionalWithASingleAccountButMustBeOwn() throws Exception {
        Customer alice = customer("100.00", false);
        Customer bob = customer("10.00", false);
        Customer agent = customer("0.00", true);

        send(alice, bob.accountNumber(), "10", alice.accountNumber()).andExpect(status().isOk());
        withdraw(alice, agent.accountNumber(), "10", bob.accountNumber())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.senderaccountnotfound"));
        assertThat(balance(alice)).isEqualByComparingTo("90.00");
    }

    // ------------------------------------------------------------------------------------------ report

    @Test
    void revenueReportShowsFeesBonusesAndNetForThePeriod() throws Exception {
        LocalDate today = LocalDate.now(RevenueReportService.ZONE);
        JsonNode before = report(today, today, "day").path("totals");

        Customer alice = customer("2000.00", false);
        Customer bob = customer("10.00", false);
        Customer agent = customer("0.00", true);
        send(alice, bob.accountNumber(), "500", null).andExpect(status().isOk()); // fee 4.75
        withdraw(alice, agent.accountNumber(), "40").andExpect(status().isOk()); // fee 1.00
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> signupBonusService.grant(bob.accountNumber())); // bonus 5.00

        JsonNode report = report(today, today, "day");
        assertThat(report.path("periods")).hasSize(1);
        JsonNode after = report.path("totals");
        assertThat(delta(after, before, "transferFees")).isEqualByComparingTo("4.75");
        assertThat(delta(after, before, "withdrawalFees")).isEqualByComparingTo("1.00");
        assertThat(delta(after, before, "totalFees")).isEqualByComparingTo("5.75");
        assertThat(delta(after, before, "bonusCost")).isEqualByComparingTo("5.00");
        assertThat(delta(after, before, "netRevenue")).isEqualByComparingTo("0.75");
        assertThat(delta(after, before, "transferVolume")).isEqualByComparingTo("500.00");
        assertThat(delta(after, before, "withdrawalVolume")).isEqualByComparingTo("40.00");
        assertThat(after.path("feeTransactions").asLong() - before.path("feeTransactions").asLong()).isEqualTo(2);
        assertThat(report.path("feeRevenueAccountBalance").decimalValue()).isEqualByComparingTo(feeBalanceBefore.add(new BigDecimal("5.75")));
    }

    @Test
    void revenueReportGroupsByMonthOrYearAndValidatesInput() throws Exception {
        JsonNode months = report(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 12, 3), "month");
        assertThat(months.path("periods")).hasSize(12);
        assertThat(months.path("periods").get(0).path("period").asText()).isEqualTo("2026-01");
        assertThat(months.path("periods").get(11).path("period").asText()).isEqualTo("2026-12");

        JsonNode years = report(LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1), "year");
        assertThat(years.path("periods")).hasSize(2);
        assertThat(years.path("periods").get(1).path("period").asText()).isEqualTo("2026");

        reportRequest("2026-12-01", "2026-01-01", "month").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invaliddaterange"));
        reportRequest("2026-01-01", "2026-12-01", "week").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invalidgroupby"));
        reportRequest("2020-01-01", "2026-12-01", "day").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invaliddaterange"));

        Customer alice = customer("10.00", false);
        mockMvc
            .perform(get(REPORT).param("from", "2026-01-01").param("to", "2026-01-31").with(user(alice.login())))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ backfill migration

    @Test
    void backfillCreditsPastFeesAndFixesTheSenderDebit() throws Exception {
        Customer alice = customer("100.00", false);
        Customer bob = customer("10.00", false);
        // An old-style transfer: sender debited amount + fee, fee row recorded, but ledger only has amount and no fee entry
        String ref = "ZMCT-BACKFILL-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Long txId = jdbcTemplate.queryForObject("select nextval('sequence_generator')", Long.class);
            jdbcTemplate.update(
                "insert into transaction_record (id, transaction_type, send_amount, receive_amount, exchange_rate, currency_send_amount, " +
                "currency_receive_amount, transaction_status, transaction_reference, risk_score, fraud_status, created_at, transaction_date, " +
                "sender_id, receiver_id) values (?, 'TRANSFER', 200, 200, 1.90, 'USD', 'USD', 'COMPLETED', ?, 0, 'CLEAN', now(), now(), ?, ?)",
                txId,
                ref,
                alice.account().getId(),
                bob.account().getId()
            );
            jdbcTemplate.update(
                "insert into transaction_fee (id, amount, currency, type, transaction_id) values (nextval('sequence_generator'), 1.90, 'USD', 'transaction fee', ?)",
                txId
            );
            jdbcTemplate.update(
                "insert into ledger_entry (id, amount, currency, entry_type, created_at, account_id, transaction_id) values " +
                "(nextval('sequence_generator'), 200, 'USD', 'DEBIT', now(), ?, ?), (nextval('sequence_generator'), 200, 'USD', 'CREDIT', now(), ?, ?)",
                alice.account().getId(),
                txId,
                bob.account().getId(),
                txId
            );
        });

        runBackfillChangeset();

        assertLedger(
            ref,
            Map.of(
                "DEBIT " + alice.accountNumber(),
                "201.90",
                "CREDIT " + bob.accountNumber(),
                "200.00",
                "CREDIT " + SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER,
                "1.90"
            )
        );
        assertThat(balance(alice)).isEqualByComparingTo("100.00"); // customer balances are never touched
        assertThat(systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER)).isEqualByComparingTo(feeBalanceBefore.add(new BigDecimal("1.90")));

        // running it again changes nothing
        runBackfillChangeset();
        assertThat(systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER)).isEqualByComparingTo(feeBalanceBefore.add(new BigDecimal("1.90")));
    }

    /** Executes the SQL of changeset 20261114000000-2 exactly as written in the changelog. */
    private void runBackfillChangeset() throws Exception {
        String xml = new ClassPathResource("config/liquibase/changelog/20261114000000_fee_revenue.xml").getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("id=\"20261114000000-2\".*?<sql>(.*?)</sql>", Pattern.DOTALL).matcher(xml);
        assertThat(m.find()).isTrue();
        String sql = m.group(1).replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            // in a real database this changeset runs before the append-only trigger (20261116000000-3) exists
            jdbcTemplate.execute(MoneyTestData.LEDGER_MAINTENANCE);
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    jdbcTemplate.execute(statement);
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------ helpers

    private void assertLedger(String reference, Map<String, String> expected) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select l.entry_type, a.account_number, l.amount from ledger_entry l join bank_account a on a.id = l.account_id " +
            "join transaction_record t on t.id = l.transaction_id where t.transaction_reference = ?",
            reference
        );
        Map<String, BigDecimal> actual = new LinkedHashMap<>();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            BigDecimal amount = (BigDecimal) r.get("amount");
            actual.put(r.get("entry_type") + " " + r.get("account_number"), amount);
            if ("DEBIT".equals(r.get("entry_type"))) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
        assertThat(actual).hasSize(expected.size());
        expected.forEach((k, v) -> assertThat(actual.get(k)).as(k).isEqualByComparingTo(v));
        assertThat(debits).isEqualByComparingTo(credits);
    }

    private ResultActions send(Customer from, String to, String amount, String senderAccount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionType", "TRANSFER");
        body.put("sendAmount", new BigDecimal(amount));
        body.put("description", "test");
        body.put("receiverAccountNumber", to);
        body.put("currencySendAmount", "USD");
        body.put("currencyReceiveAmount", "USD");
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", PIN);
        if (senderAccount != null) {
            body.put("senderAccountNumber", senderAccount);
        }
        return mockMvc.perform(post(SEND).with(user(from.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions withdraw(Customer from, String agent, String amount) throws Exception {
        return withdraw(from, agent, amount, null);
    }

    private ResultActions withdraw(Customer from, String agent, String amount, String senderAccount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentAccountNumber", agent);
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", PIN);
        if (senderAccount != null) {
            body.put("senderAccountNumber", senderAccount);
        }
        return mockMvc.perform(
            post(WITHDRAWAL).with(user(from.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))
        );
    }

    private ResultActions reportRequest(String from, String to, String groupBy) throws Exception {
        return mockMvc.perform(
            get(REPORT)
                .param("from", from)
                .param("to", to)
                .param("groupBy", groupBy)
                .with(user("report-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)))
        );
    }

    private JsonNode report(LocalDate from, LocalDate to, String groupBy) throws Exception {
        String body = reportRequest(from.toString(), to.toString(), groupBy).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static BigDecimal delta(JsonNode after, JsonNode before, String field) {
        return after.path(field).decimalValue().subtract(before.path(field).decimalValue());
    }

    private String ref(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("transactionReference").asText();
    }

    private BigDecimal balance(Customer c) {
        return bankAccountRepository.findById(c.account().getId()).orElseThrow().getBalance();
    }

    private BigDecimal systemBalance(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private static String newAccountNumber() {
        return "2512" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private BankAccount extraAccount(User user, String balance) {
        return new TransactionTemplate(transactionManager).execute(s ->
            bankAccountRepository.saveAndFlush(
                new BankAccount()
                    .accountNumber(newAccountNumber())
                    .balance(new BigDecimal(balance))
                    .currency(Currency.USD)
                    .status(AccountStatus.ACTIVE)
                    .accountType(BankAccountType.CUSTOMER)
                    .createdAt(Instant.now())
                    .user(user)
            )
        );
    }

    private Customer customer(String balance, boolean agent) {
        Customer created = new TransactionTemplate(transactionManager).execute(s -> {
            String login = "it-fee-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
            User user = new User();
            user.setLogin(login);
            user.setEmail(login);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setActivated(true);
            user.setFirstName("First");
            user.setLastName("Last");
            user.setLangKey("en");
            user = userRepository.saveAndFlush(user);
            BankAccount account = bankAccountRepository.saveAndFlush(
                new BankAccount()
                    .accountNumber(newAccountNumber())
                    .balance(new BigDecimal(balance))
                    .currency(Currency.USD)
                    .status(AccountStatus.ACTIVE)
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
                    .isAgent(agent)
                    .kycStatus(KycStatus.VERIFIED)
                    .user(user)
            );
            return new Customer(user, account);
        });
        createdUserIds.add(created.user().getId());
        transactionPinService.setPin(created.user(), PASSWORD, PIN);
        return created;
    }

    @AfterEach
    void cleanUp() {
        // The test pool uses auto-commit=false: run the deletes in a transaction or they are rolled back
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createdUserIds.forEach(this::deleteUserData);
            jdbcTemplate.update(
                "update bank_account set balance = ? where account_number = ?",
                feeBalanceBefore,
                SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER
            );
            jdbcTemplate.update("update bank_account set balance = ? where account_number = ?", promoBalanceBefore, SystemAccountService.PROMO_ACCOUNT_NUMBER);
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
        jdbcTemplate.update("delete from transaction_limit where account_id in " + accounts);
        jdbcTemplate.update("delete from idempotency_record where user_id = ?", userId);
        jdbcTemplate.update("delete from transaction_pin where user_id = ?", userId);
        jdbcTemplate.update("delete from user_details_account where user_id = ?", userId);
        jdbcTemplate.update("delete from bank_account where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user where id = ?", userId);
    }
}
