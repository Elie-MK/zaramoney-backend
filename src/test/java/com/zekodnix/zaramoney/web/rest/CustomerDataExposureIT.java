package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.utils.MaskingUtils;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guard for every customer-readable endpoint: after a real transfer between two users, neither user can read the
 * other's login, e-mail, surname, full account number, balance or internal user / account id. Allowed about the
 * counterparty: display name "Firstname L." and the masked account number ("********1234").
 * Add any new customer GET endpoint to {@link #everythingVisibleTo}.
 */
@IntegrationTest
@AutoConfigureMockMvc
class CustomerDataExposureIT {

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
    void neitherSideOfATransferSeesTheOthersPrivateData() throws Exception {
        Customer alice = data.customer(spec().balance("137.45"));
        Customer bob = data.customer(spec().balance("212.30"));
        rename(alice, "Alice", "Secretsurnamea");
        rename(bob, "Bobby", "Secretsurnameb");

        String sendResponse = send(alice, bob, "10", UUID.randomUUID().toString());
        long txId = objectMapper.readTree(sendResponse).get("id").asLong();

        List<JsonNode> aliceSees = everythingVisibleTo(alice, txId);
        aliceSees.add(objectMapper.readTree(sendResponse));
        List<JsonNode> bobSees = everythingVisibleTo(bob, txId);

        assertNothingPrivateOf(bob, "212.30", "220.30", aliceSees);
        assertNothingPrivateOf(alice, "137.45", "127.45", bobSees);

        // what IS shown about the counterparty: display name and masked number
        assertThat(texts(bobSees)).contains("Alice S.", MaskingUtils.maskAccountNumber(alice.accountNumber()));
        assertThat(texts(aliceSees)).contains("Bobby S.", MaskingUtils.maskAccountNumber(bob.accountNumber()));
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** Every customer GET endpoint, as this user. */
    private List<JsonNode> everythingVisibleTo(Customer c, long txId) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (String url : List.of(
            "/api/plus/transaction-records?size=50",
            "/api/plus/transaction-records/" + txId,
            "/api/ledger-entries/plus/me?size=50",
            "/api/ledger-entries/plus/transaction/" + txId,
            "/api/plus/notifications",
            "/api/plus/notifications/unread-count",
            "/api/user-account",
            "/api/account",
            "/api/account/sessions",
            "/api/plus/security/pin"
        )) {
            String body = mockMvc.perform(get(url).with(user(c.login()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            out.add(objectMapper.readTree(body));
        }
        return out;
    }

    private void assertNothingPrivateOf(Customer other, String balanceBefore, String balanceAfter, List<JsonNode> seen) {
        List<String> text = texts(seen);
        List<BigDecimal> numbers = numbers(seen);
        String surname = jdbcTemplate.queryForObject("select last_name from jhi_user where id = ?", String.class, other.user().getId());
        assertThat(text)
            .as("login / e-mail / surname / full account number of " + other.login())
            .noneMatch(t -> t.contains(other.login()) || t.contains(surname) || t.contains(other.accountNumber()));
        assertThat(numbers)
            .as("balance or internal ids of " + other.login())
            .noneMatch(n ->
                n.compareTo(new BigDecimal(balanceBefore)) == 0 ||
                n.compareTo(new BigDecimal(balanceAfter)) == 0 ||
                n.compareTo(BigDecimal.valueOf(other.user().getId())) == 0 ||
                n.compareTo(BigDecimal.valueOf(other.account().getId())) == 0
            );
    }

    private static List<String> texts(List<JsonNode> nodes) {
        List<String> out = new ArrayList<>();
        nodes.forEach(n -> collect(n, out, null));
        return out;
    }

    private static List<BigDecimal> numbers(List<JsonNode> nodes) {
        List<BigDecimal> out = new ArrayList<>();
        nodes.forEach(n -> collect(n, null, out));
        return out;
    }

    private static void collect(JsonNode node, List<String> texts, List<BigDecimal> numbers) {
        if (node.isContainerNode()) {
            node.forEach(child -> collect(child, texts, numbers));
        } else if (node.isTextual() && texts != null) {
            texts.add(node.asText());
        } else if (node.isNumber() && numbers != null) {
            numbers.add(node.decimalValue());
        }
    }

    private String send(Customer from, Customer to, String amount, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal(amount));
        body.put("idempotencyKey", key);
        body.put("pin", MoneyTestData.PIN);
        return mockMvc
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
    }

    private void rename(Customer c, String first, String last) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
            jdbcTemplate.update("update jhi_user set first_name = ?, last_name = ? where id = ?", first, last, c.user().getId())
        );
    }
}
