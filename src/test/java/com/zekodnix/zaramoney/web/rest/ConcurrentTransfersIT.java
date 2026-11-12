package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.LedgerReconciliationService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Real lock contention on customer transfers. The other ITs share a 1-connection pool, so their "parallel" requests
 * run one after another; this class gets its own context with 8 connections, so transactions really overlap and
 * wait on each other's row locks.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=8")
class ConcurrentTransfersIT {

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
    private TransactionPinService transactionPinService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LedgerReconciliationService reconciliationService;

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
    void parallelSendsNeverOverdrawTheMinimumBalance() throws Exception {
        Customer alice = data.customer(spec().balance("100.00"));
        Customer bob = data.customer(spec().balance("0.00"));

        // 10 x 20 in parallel; 100 - 20k must stay >= 5 (minimum balance, fee 0 below 100.99): exactly 4 can pass
        List<MvcResult> results = parallel(10, i -> send(alice, bob, "20", UUID.randomUUID().toString()));

        assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 200)).hasSize(4);
        assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 400)).hasSize(6);
        assertThat(results.stream().map(ConcurrentTransfersIT::body).filter(b -> b.contains("error.")))
            .allMatch(b -> b.contains("error.minimumbalance") || b.contains("error.insufficientbalance"));
        assertThat(data.balance(alice)).isEqualByComparingTo("20.00");
        assertThat(data.balance(bob)).isEqualByComparingTo("80.00");
        assertBooksBalance(alice, bob);
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        // A→B and B→A at the same time: both lock both rows; a consistent lock order means nobody waits in a cycle
        Customer alice = data.customer(spec().balance("500.00"));
        Customer bob = data.customer(spec().balance("500.00"));

        List<MvcResult> results = parallel(20, i ->
            i % 2 == 0 ? send(alice, bob, "10", UUID.randomUUID().toString()) : send(bob, alice, "10", UUID.randomUUID().toString())
        );

        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).as(body(r)).isEqualTo(200));
        assertThat(data.balance(alice)).isEqualByComparingTo("500.00");
        assertThat(data.balance(bob)).isEqualByComparingTo("500.00");
        assertBooksBalance(alice, bob);
    }

    @Test
    void aParallelDoubleClickDebitsOnce() throws Exception {
        Customer alice = data.customer(spec().balance("100.00"));
        Customer bob = data.customer(spec().balance("0.00"));
        String key = UUID.randomUUID().toString();

        List<MvcResult> results = parallel(8, i -> send(alice, bob, "10", key));

        // one transfer; the others replay it (200, same reference) or see it still in progress (409)
        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).as(body(r)).isIn(200, 409));
        assertThat(
            results
                .stream()
                .filter(r -> r.getResponse().getStatus() == 200)
                .map(r -> reference(r))
                .distinct()
        ).hasSize(1);
        assertThat(data.balance(alice)).isEqualByComparingTo("90.00");
        assertThat(data.balance(bob)).isEqualByComparingTo("10.00");
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from transaction_record where sender_id = ? and transaction_type = 'TRANSFER'",
                Integer.class,
                alice.account().getId()
            )
        ).isEqualTo(1);
    }

    @Test
    void manySendersToOneReceiverLoseNothing() throws Exception {
        Customer carol = data.customer(spec().balance("0.00"));
        List<Customer> senders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            senders.add(data.customer(spec().balance("100.00")));
        }

        List<MvcResult> results = parallel(12, i -> send(senders.get(i % 6), carol, "10", UUID.randomUUID().toString()));

        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).as(body(r)).isEqualTo(200));
        assertThat(data.balance(carol)).isEqualByComparingTo("120.00");
        for (Customer s : senders) {
            assertThat(data.balance(s)).isEqualByComparingTo("80.00");
        }
        List<Customer> all = new ArrayList<>(senders);
        all.add(carol);
        assertBooksBalance(all.toArray(Customer[]::new));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private interface IndexedCall {
        MvcResult call(int index) throws Exception;
    }

    private MvcResult send(Customer from, Customer to, String amount, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", key);
        body.put("pin", MoneyTestData.PIN);
        return mockMvc
            .perform(post(SEND).with(user(from.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
            .andReturn();
    }

    private List<MvcResult> parallel(int threads, IndexedCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<MvcResult> task = () -> {
                    start.await();
                    return call.call(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Every balance still matches its ledger chain, and every transaction is balanced. */
    private void assertBooksBalance(Customer... customers) {
        ReconciliationReportDTO report = reconciliationService.reconcile();
        for (Customer c : customers) {
            assertThat(report.balanceDrifts()).noneMatch(d -> d.accountNumber().equals(c.accountNumber()));
            assertThat(report.chainBreaks()).noneMatch(b -> b.accountNumber().equals(c.accountNumber()));
        }
        assertThat(report.unbalancedTransactions()).isEmpty();
    }

    private String reference(MvcResult r) {
        try {
            return objectMapper.readTree(r.getResponse().getContentAsString()).get("transactionReference").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String body(MvcResult r) {
        try {
            return r.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
