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
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.SystemAccountService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Phase 3 transfer rules: account status, currency, server-side type, fee column, amount precision, 4xx errors. */
@IntegrationTest
@AutoConfigureMockMvc
class Phase3RulesIT {

    private static final String SEND = "/api/plus/transaction-records/send";
    private static final String WITHDRAWAL = "/api/plus/transaction-records/withdrawal";

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
        data.cleanUp();
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
            jdbcTemplate.update(
                "update bank_account set balance = ? where account_number = ?",
                feeBalanceBefore,
                SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER
            )
        );
    }

    // ------------------------------------------------------------------------------------------ account status

    @Test
    void suspendedOrClosedSenderCannotPay() throws Exception {
        Customer bob = data.customer(spec());
        for (AccountStatus status : new AccountStatus[] { AccountStatus.SUSPENDED, AccountStatus.CLOSED }) {
            Customer alice = data.customer(spec().status(status));
            send(alice, bob.accountNumber(), "10", null).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("error.accountnotactive"));
            assertThat(data.balance(alice)).isEqualByComparingTo("100.00");
        }
    }

    @Test
    void suspendedOrClosedReceiverCannotBePaid() throws Exception {
        Customer alice = data.customer(spec());
        Customer closed = data.customer(spec().status(AccountStatus.CLOSED));
        Customer suspendedAgent = data.customer(spec().status(AccountStatus.SUSPENDED).agent());

        send(alice, closed.accountNumber(), "10", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.receivernotactive"));
        withdraw(alice, suspendedAgent.accountNumber(), "10")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.receivernotactive"));
        assertThat(data.balance(alice)).isEqualByComparingTo("100.00");
        assertThat(data.balance(closed)).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------------------------------------ currency

    @Test
    void currenciesMustMatchBecauseThereIsNoConversion() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Customer tnd = data.customer(spec().currency(Currency.TND));

        // requested currency differs from the accounts
        Map<String, Object> body = sendBody(bob.accountNumber(), "10");
        body.put("currencySendAmount", "TND");
        post(SEND, alice, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.currencymismatch"));

        // accounts in different currencies
        send(alice, tnd.accountNumber(), "10", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.currencymismatch"));
        assertThat(data.balance(alice)).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------------------------------------ server-side type, fee column

    @Test
    void transactionTypeIsSetByTheServerAndCurrenciesAreOptional() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());

        // a client claiming DEPOSIT still gets a TRANSFER
        String ref = ref(send(alice, bob.accountNumber(), "10", "DEPOSIT").andExpect(status().isOk()).andExpect(jsonPath("$.transactionType").value("TRANSFER")));
        assertThat(column(ref, "transaction_type")).isEqualTo("TRANSFER");

        // new app versions may omit transactionType and both currencies
        Map<String, Object> minimal = new LinkedHashMap<>();
        minimal.put("receiverAccountNumber", bob.accountNumber());
        minimal.put("sendAmount", new BigDecimal("10"));
        minimal.put("idempotencyKey", UUID.randomUUID().toString());
        minimal.put("pin", MoneyTestData.PIN);
        post(SEND, alice, minimal).andExpect(status().isOk()).andExpect(jsonPath("$.transactionType").value("TRANSFER"));
        assertThat(data.balance(alice)).isEqualByComparingTo("80.00");
    }

    @Test
    void feeIsStoredInItsOwnColumnAndExchangeRateIsOne() throws Exception {
        Customer alice = data.customer(spec().balance("1000.00"));
        Customer bob = data.customer(spec());
        Customer agent = data.customer(spec().balance("0.00").agent());

        String transfer = ref(send(alice, bob.accountNumber(), "500", null).andExpect(status().isOk()));
        assertThat(new BigDecimal(column(transfer, "fee"))).isEqualByComparingTo("4.75");
        assertThat(new BigDecimal(column(transfer, "exchange_rate"))).isEqualByComparingTo("1");

        // 20 → 0.8% = 0.16 (was 8% = 1.60)
        String withdrawal = ref(withdraw(alice, agent.accountNumber(), "20").andExpect(status().isOk()).andExpect(jsonPath("$.fee").value(0.16)));
        assertThat(new BigDecimal(column(withdrawal, "fee"))).isEqualByComparingTo("0.16");
        assertThat(column(withdrawal, "transaction_type")).isEqualTo("WITHDRAWAL");
        assertThat(data.balance(alice)).isEqualByComparingTo("475.09"); // 1000 - 504.75 - 20.16
    }

    @Test
    void amountsWithMoreThanTwoDecimalsAreRejected() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Customer agent = data.customer(spec().agent());

        send(alice, bob.accountNumber(), "10.005", null).andExpect(status().isBadRequest());
        withdraw(alice, agent.accountNumber(), "10.001").andExpect(status().isBadRequest());
        assertThat(data.balance(alice)).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------------------------------------ 4xx instead of 500

    @Test
    void userWithoutAWalletGets404OnTheAccountScreen() throws Exception {
        var user = data.userWithoutAccount();
        mockMvc
            .perform(get("/api/user-account").with(user(user.getLogin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.noaccount"));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private Map<String, Object> sendBody(String to, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionType", "TRANSFER");
        body.put("sendAmount", new BigDecimal(amount));
        body.put("description", "test");
        body.put("receiverAccountNumber", to);
        body.put("currencySendAmount", "USD");
        body.put("currencyReceiveAmount", "USD");
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", MoneyTestData.PIN);
        return body;
    }

    private ResultActions send(Customer from, String to, String amount, String type) throws Exception {
        Map<String, Object> body = sendBody(to, amount);
        if (type != null) {
            body.put("transactionType", type);
        }
        return post(SEND, from, body);
    }

    private ResultActions withdraw(Customer from, String agent, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentAccountNumber", agent);
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("pin", MoneyTestData.PIN);
        return post(WITHDRAWAL, from, body);
    }

    private ResultActions post(String url, Customer from, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(user(from.login()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body))
        );
    }

    private String ref(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("transactionReference").asText();
    }

    private String column(String reference, String column) {
        return jdbcTemplate.queryForObject(
            "select " + column + "::text from transaction_record where transaction_reference = ?",
            String.class,
            reference
        );
    }
}
