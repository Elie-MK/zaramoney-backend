package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    }

    @AfterEach
    void tearDown() {
        data.cleanUp();
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
            createdAccountNumbers.forEach(n -> jdbcTemplate.update("delete from bank_account where account_number = ?", n))
        );
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
