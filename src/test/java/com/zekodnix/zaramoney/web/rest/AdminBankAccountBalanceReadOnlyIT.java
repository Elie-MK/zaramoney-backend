package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.SystemAccountService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The balance is read-only in the generated admin CRUD {@code /api/bank-accounts}: only ledger-backed flows change it.
 * Create starts at 0, PUT/PATCH keep the current balance, and an update built from a stale read never undoes a transfer.
 * An account in use (balance or history) keeps its number, currency, owner and type, and cannot be deleted.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminBankAccountBalanceReadOnlyIT {

    private static final String API = "/api/bank-accounts";

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
    private BigDecimal feeBalanceBefore;
    private final List<String> createdAccountNumbers = new ArrayList<>();

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
        feeBalanceBefore = data.systemBalance(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER);
    }

    @AfterEach
    void tearDown() {
        data.cleanUp(); // also deletes the fee ledger entries of our transfers: put the fee account balance back
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            createdAccountNumbers.forEach(n -> jdbcTemplate.update("delete from bank_account where account_number = ?", n));
            jdbcTemplate.update(
                "update bank_account set balance = ? where account_number = ?",
                feeBalanceBefore,
                SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER
            );
        });
    }

    @Test
    void createAlwaysStartsAtZero() throws Exception {
        String accountNumber = "2512" + UUID.randomUUID().toString().replaceAll("\\D", "").concat("00000000").substring(0, 8);
        createdAccountNumbers.add(accountNumber);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountNumber", accountNumber);
        body.put("balance", new BigDecimal("1000000.00"));
        body.put("currency", "USD");
        body.put("status", "ACTIVE");
        body.put("accountType", "CUSTOMER");
        body.put("createdAt", "2026-11-12T10:00:00Z");

        mockMvc
            .perform(post(API).with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balance").value(0));
        assertThat(bankAccountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void putUpdatesOtherFieldsButKeepsTheBalance() throws Exception {
        Customer bob = data.customer(spec());
        Map<String, Object> dto = read(bob);
        dto.put("balance", new BigDecimal("999999.00"));
        dto.put("status", "SUSPENDED");

        adminPut(bob, dto).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100.0)).andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(data.balance(bob)).isEqualByComparingTo("100.00");
        assertThat(bankAccountRepository.findById(bob.account().getId()).orElseThrow().getStatus().name()).isEqualTo("SUSPENDED");
    }

    @Test
    void patchIgnoresTheBalance() throws Exception {
        Customer bob = data.customer(spec());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", bob.account().getId());
        dto.put("balance", new BigDecimal("999999.00"));
        dto.put("status", "SUSPENDED");

        mockMvc
            .perform(
                patch(API + "/" + bob.account().getId())
                    .with(asAdmin())
                    .contentType("application/merge-patch+json")
                    .content(objectMapper.writeValueAsBytes(dto))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(100.0))
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(data.balance(bob)).isEqualByComparingTo("100.00");
    }

    @Test
    void aPutBuiltFromAStaleReadCannotUndoATransfer() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Map<String, Object> stale = read(bob); // balance 100.00

        send(alice, bob, "10");
        assertThat(data.balance(bob)).isEqualByComparingTo("110.00");

        adminPut(bob, stale).andExpect(status().isOk());
        assertThat(data.balance(bob)).isEqualByComparingTo("110.00");
    }

    @Test
    void parallelTransfersAndAdminUpdatesLoseNoMoney() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Map<String, Object> stale = read(bob);
        int rounds = 5;

        List<Integer> statuses = runInParallel(rounds * 2, i ->
            i % 2 == 0 ? send(alice, bob, "10") : adminPut(bob, stale).andReturn().getResponse().getStatus()
        );

        assertThat(statuses).containsOnly(200);
        assertThat(data.balance(alice)).isEqualByComparingTo("50.00");
        assertThat(data.balance(bob)).isEqualByComparingTo("150.00");
    }

    // ------------------------------------------------------------------------------------------ identity of an account in use

    @Test
    void aFundedAccountKeepsItsNumberCurrencyOwnerAndType() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Map<String, Object> original = read(bob);

        List<Map<String, Object>> changes = List.of(
            Map.of("currency", "TND"),
            Map.of("accountType", "SYSTEM"),
            Map.of("accountNumber", "251299999999"),
            Map.of("user", Map.of("id", alice.user().getId(), "login", alice.login()))
        );
        for (Map<String, Object> change : changes) {
            Map<String, Object> full = new LinkedHashMap<>(original);
            full.putAll(change);
            adminPut(bob, full).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("error.accountinuse"));

            Map<String, Object> partial = new LinkedHashMap<>(change);
            partial.put("id", bob.account().getId());
            adminPatch(bob, partial).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("error.accountinuse"));
        }
        // PUT without an owner would detach the wallet from its user
        Map<String, Object> noOwner = new LinkedHashMap<>(original);
        noOwner.remove("user");
        adminPut(bob, noOwner).andExpect(status().isConflict());

        assertThat(read(bob)).isEqualTo(original);
    }

    @Test
    void anEmptyAccountWithHistoryIsStillInUse() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec().balance("0.00"));
        assertThat(send(alice, bob, "10")).isEqualTo(200);
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
            jdbcTemplate.update("update bank_account set balance = 0 where id = ?", bob.account().getId())
        );

        adminPatch(bob, Map.of("id", bob.account().getId(), "currency", "TND")).andExpect(status().isConflict());
        mockMvc.perform(delete(API + "/" + bob.account().getId()).with(asAdmin())).andExpect(status().isConflict());
        assertThat(bankAccountRepository.findById(bob.account().getId())).isPresent();
    }

    @Test
    void anUnusedAccountCanStillBeFixedOrDeleted() throws Exception {
        Customer alice = data.customer(spec());
        Customer fresh = data.customer(spec().balance("0.00"));

        adminPatch(fresh, Map.of("id", fresh.account().getId(), "currency", "TND", "user", Map.of("id", alice.user().getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("TND"))
            .andExpect(jsonPath("$.user.id").value(alice.user().getId()));

        mockMvc.perform(delete(API + "/" + fresh.account().getId()).with(asAdmin())).andExpect(status().isNoContent());
        assertThat(bankAccountRepository.findById(fresh.account().getId())).isEmpty();
    }

    @Test
    void fundedCustomerAndSystemAccountsCannotBeDeleted() throws Exception {
        Customer bob = data.customer(spec());
        mockMvc
            .perform(delete(API + "/" + bob.account().getId()).with(asAdmin()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.accountinuse"));
        assertThat(data.balance(bob)).isEqualByComparingTo("100.00");

        // a transfer with a fee gives the SYSTEM fee account a ledger history
        Customer alice = data.customer(spec().balance("1000.00"));
        assertThat(send(alice, bob, "500")).isEqualTo(200);
        Long feeAccountId = bankAccountRepository.findByAccountNumber(SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER).orElseThrow().getId();
        mockMvc.perform(delete(API + "/" + feeAccountId).with(asAdmin())).andExpect(status().isConflict());
        assertThat(bankAccountRepository.findById(feeAccountId)).isPresent();
    }

    @Test
    void patchingTheOwnerSwitchesTheReferenceAndNeverRenamesAUser() throws Exception {
        Customer alice = data.customer(spec());
        Customer fresh = data.customer(spec().balance("0.00"));
        String freshLogin = fresh.login();

        // same owner id with a different login: must not rename the owner
        adminPatch(fresh, Map.of("id", fresh.account().getId(), "user", Map.of("id", fresh.user().getId(), "login", "renamed@x.test")))
            .andExpect(status().isOk());
        assertThat(userRepository.findById(fresh.user().getId()).orElseThrow().getLogin()).isEqualTo(freshLogin);

        // switching owner leaves both users untouched
        adminPatch(fresh, Map.of("id", fresh.account().getId(), "user", Map.of("id", alice.user().getId(), "login", "renamed@x.test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(alice.user().getId()));
        assertThat(userRepository.findById(fresh.user().getId()).orElseThrow().getLogin()).isEqualTo(freshLogin);
        assertThat(userRepository.findById(alice.user().getId()).orElseThrow().getLogin()).isEqualTo(alice.login());

        adminPatch(fresh, Map.of("id", fresh.account().getId(), "user", Map.of("id", Long.MAX_VALUE)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.usernotfound"));
    }

    @Test
    void deleteOfAnUnknownAccountIs404() throws Exception {
        mockMvc.perform(delete(API + "/" + Long.MAX_VALUE).with(asAdmin())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------ helpers

    private RequestPostProcessor asAdmin() {
        return user("it-crud-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private Map<String, Object> read(Customer c) throws Exception {
        String json = mockMvc
            .perform(get(API + "/" + c.account().getId()).with(asAdmin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private org.springframework.test.web.servlet.ResultActions adminPut(Customer c, Map<String, Object> dto) throws Exception {
        return mockMvc.perform(
            put(API + "/" + c.account().getId())
                .with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(dto))
        );
    }

    private org.springframework.test.web.servlet.ResultActions adminPatch(Customer c, Map<String, Object> dto) throws Exception {
        return mockMvc.perform(
            patch(API + "/" + c.account().getId())
                .with(asAdmin())
                .contentType("application/merge-patch+json")
                .content(objectMapper.writeValueAsBytes(dto))
        );
    }

    private int send(Customer from, Customer to, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", MoneyTestData.PIN);
        return mockMvc
            .perform(
                post("/api/plus/transaction-records/send")
                    .with(user(from.login()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private interface IndexedCall {
        int call(int index) throws Exception;
    }

    private List<Integer> runInParallel(int threads, IndexedCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return call.call(index);
                };
                futures.add(pool.submit(task));
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
}
