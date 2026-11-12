package com.zekodnix.zaramoney.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionLimit;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.repository.TransactionPinRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.SystemAccountService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end tests of the Phase 2 money-movement rules (TODO.md items 3–10).
 * Not @Transactional on purpose: the PIN check runs in its own transaction (REQUIRES_NEW) and must see committed data.
 * Every test creates its own users and deletes them afterwards.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TransactionFlowIT {

    private static final String PASSWORD = "Str0ng-Passw0rd";
    private static final String PIN = "4826";
    private static final String SEND = "/api/plus/transaction-records/send";

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
    private TransactionLimitRepository transactionLimitRepository;

    @Autowired
    private TransactionPinRepository transactionPinRepository;

    @Autowired
    private TransactionPinService transactionPinService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();

    record Customer(User user, BankAccount account) {
        String login() {
            return user.getLogin();
        }

        String accountNumber() {
            return account.getAccountNumber();
        }
    }

    // ------------------------------------------------------------------------------------------ tests

    @Test
    void sendMovesMoneyAndReturnsAReceiptWithoutTheReceiversData() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);

        send(alice, bob.accountNumber(), "50", key())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.direction").value("SENT"))
            .andExpect(jsonPath("$.amount").value(50))
            .andExpect(jsonPath("$.fee").value(0))
            .andExpect(jsonPath("$.counterpartyAccountNumber").value(startsWith("********")))
            .andExpect(jsonPath("$.accountNumber").value(alice.accountNumber()))
            .andExpect(jsonPath("$.sender").doesNotExist())
            .andExpect(jsonPath("$.receiver").doesNotExist())
            .andExpect(content().string(not(containsString(bob.login()))))
            .andExpect(content().string(not(containsString("balance"))));

        assertThat(balance(alice)).isEqualByComparingTo("50.00");
        assertThat(balance(bob)).isEqualByComparingTo("60.00");
    }

    @Test
    void feeIsIncludedInTheBalanceCheck() throws Exception {
        // 1001 has a 9.51 fee: old code checked 1006 - 1001 >= 5 and then debited 1010.51
        Customer alice = customer("1006.00", KycStatus.VERIFIED, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);

        send(alice, bob.accountNumber(), "1001", key())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.insufficientbalance"));
        assertThat(balance(alice)).isEqualByComparingTo("1006.00");
    }

    @Test
    void userWithPendingKycCannotSend() throws Exception {
        Customer alice = customer("100.00", KycStatus.PENDING, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);

        send(alice, bob.accountNumber(), "10", key()).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.kycnotverified"));
        assertThat(balance(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void pinIsLockedAfterFiveWrongAttemptsEvenThoughEachTransferRollsBack() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);

        for (int i = 1; i < TransactionPinService.MAX_FAILED_ATTEMPTS; i++) {
            send(alice, bob.accountNumber(), "10", key(), "9999").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invalidpin"));
        }
        send(alice, bob.accountNumber(), "10", key(), "9999").andExpect(status().isLocked()).andExpect(jsonPath("$.message").value("error.pinlocked"));
        // correct PIN is refused while locked
        send(alice, bob.accountNumber(), "10", key(), PIN).andExpect(status().isLocked());

        assertThat(transactionPinRepository.findOneByUserId(alice.user().getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
        assertThat(balance(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void userWithoutPinMustSetOneFirst() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, false);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);

        mockMvc.perform(get("/api/plus/security/pin").with(user(alice.login()))).andExpect(jsonPath("$.pinSet").value(false));
        send(alice, bob.accountNumber(), "10", key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.pinnotset"));

        putPin(alice, "wrong-password", "4826").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invalidpassword"));
        putPin(alice, PASSWORD, "1234").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.weakpin"));
        putPin(alice, PASSWORD, "4826").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/plus/security/pin").with(user(alice.login()))).andExpect(jsonPath("$.pinSet").value(true));
        send(alice, bob.accountNumber(), "10", key()).andExpect(status().isOk());
    }

    @Test
    void idempotencyIsPerUserAndRejectsADifferentPayload() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, true);
        Customer bob = customer("100.00", KycStatus.VERIFIED, true);
        Customer carol = customer("10.00", KycStatus.VERIFIED, true);
        String key = key();

        String first = send(alice, carol.accountNumber(), "20", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String replay = send(alice, carol.accountNumber(), "20", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ref(replay)).isEqualTo(ref(first));
        assertThat(balance(alice)).isEqualByComparingTo("80.00"); // debited once

        // same key, different amount -> conflict, no money moved
        send(alice, carol.accountNumber(), "21", key).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("error.idempotencykeyreused"));
        assertThat(balance(alice)).isEqualByComparingTo("80.00");

        // another user with the same key gets their own new transaction, never alice's
        String bobs = send(bob, carol.accountNumber(), "20", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ref(bobs)).isNotEqualTo(ref(first));
        assertThat(balance(bob)).isEqualByComparingTo("80.00");
    }

    @Test
    void dailyLimitIsEnforced() throws Exception {
        Customer alice = customer("500.00", KycStatus.VERIFIED, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
            transactionLimitRepository.save(
                new TransactionLimit().dailyLimit(new BigDecimal("100")).monthlyLimit(new BigDecimal("1000")).account(alice.account())
            )
        );

        send(alice, bob.accountNumber(), "60", key()).andExpect(status().isOk());
        send(alice, bob.accountNumber(), "50", key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.dailylimitexceeded"));
        send(alice, bob.accountNumber(), "40", key()).andExpect(status().isOk());
        assertThat(balance(alice)).isEqualByComparingTo("400.00");
    }

    @Test
    void cannotSendToSelfOrToASystemAccount() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, true);

        send(alice, alice.accountNumber(), "10", key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.sendtomyself"));
        send(alice, SystemAccountService.PROMO_ACCOUNT_NUMBER, "10", key())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.receivernotfound"));
        mockMvc
            .perform(get("/api/bank-accounts/plus/accounts/" + SystemAccountService.PROMO_ACCOUNT_NUMBER + "/verify").with(user(alice.login())))
            .andExpect(content().string("false"));
        assertThat(balance(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void usersOnlySeeTheirOwnTransactions() throws Exception {
        Customer alice = customer("100.00", KycStatus.VERIFIED, true);
        Customer bob = customer("10.00", KycStatus.VERIFIED, true);
        Customer mallory = customer("10.00", KycStatus.VERIFIED, true);

        String body = send(alice, bob.accountNumber(), "25", key()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc
            .perform(get("/api/plus/transaction-records/" + id).with(user(bob.login())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.direction").value("RECEIVED"))
            .andExpect(jsonPath("$.fee").doesNotExist())
            .andExpect(content().string(not(containsString(alice.login()))));
        mockMvc.perform(get("/api/plus/transaction-records/" + id).with(user(mallory.login()))).andExpect(status().isNotFound());
        mockMvc
            .perform(get("/api/plus/transaction-records").with(user(mallory.login())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc
            .perform(get("/api/ledger-entries/plus/me").with(user(bob.login())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].entryType").value("CREDIT"))
            .andExpect(jsonPath("$.content[0].transactionRecord.direction").value("RECEIVED"))
            .andExpect(content().string(not(containsString("balance"))));
        mockMvc.perform(get("/api/ledger-entries/plus/transaction/" + id).with(user(mallory.login()))).andExpect(status().isNotFound());
    }

    @Test
    void loginIsLockedAfterFiveFailures() throws Exception {
        Customer alice = customer("10.00", KycStatus.VERIFIED, false);
        for (int i = 0; i < 5; i++) {
            login(alice.login(), "bad-password").andExpect(status().isUnauthorized());
        }
        login(alice.login(), PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value("error.loginlocked"));
    }

    @Test
    void usersListIsAdminOnly() throws Exception {
        Customer alice = customer("10.00", KycStatus.VERIFIED, false);
        mockMvc.perform(get("/api/users").with(user(alice.login()))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions send(Customer from, String to, String amount, String key) throws Exception {
        return send(from, to, amount, key, PIN);
    }

    private ResultActions send(Customer from, String to, String amount, String key, String pin) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionType", "TRANSFER");
        body.put("sendAmount", new BigDecimal(amount));
        body.put("description", "test");
        body.put("receiverAccountNumber", to);
        body.put("currencySendAmount", "USD");
        body.put("currencyReceiveAmount", "USD");
        body.put("idempotencyKey", key);
        body.put("pin", pin);
        return mockMvc.perform(
            post(SEND).with(user(from.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))
        );
    }

    private ResultActions putPin(Customer c, String password, String pin) throws Exception {
        return mockMvc.perform(
            put("/api/plus/security/pin")
                .with(user(c.login()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("password", password, "pin", pin)))
        );
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(
            post("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", password)))
        );
    }

    private String ref(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("transactionReference").asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private BigDecimal balance(Customer c) {
        return bankAccountRepository.findById(c.account().getId()).orElseThrow().getBalance();
    }

    private Customer customer(String balance, KycStatus kyc, boolean withPin) {
        Customer created = new TransactionTemplate(transactionManager).execute(s -> {
            String login = "it-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
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
                    .accountNumber("2512" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)))
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
                    .isAgent(false)
                    .kycStatus(kyc)
                    .user(user)
            );
            return new Customer(user, account);
        });
        createdUserIds.add(created.user().getId());
        if (withPin) {
            transactionPinService.setPin(created.user(), PASSWORD, PIN);
        }
        return created;
    }

    @AfterEach
    void cleanUp() {
        // The test pool uses auto-commit=false: run the deletes in a transaction or they are rolled back
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> createdUserIds.forEach(this::deleteUserData));
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
        // a successful login (e.g. a failed lockout assertion) leaves a session behind
        jdbcTemplate.update("delete from refresh_token where session_id in (select id from user_session where user_id = ?)", userId);
        jdbcTemplate.update("delete from user_session where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user where id = ?", userId);
    }
}
