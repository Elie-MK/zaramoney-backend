package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.util.HashMap;
import java.util.Map;
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

/** App endpoints: unread count, mark read / read all, push token update. All scoped to the current user. */
@IntegrationTest
@AutoConfigureMockMvc
class NotificationAppEndpointsIT {

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

    // ------------------------------------------------------------------------------------------ read state

    @Test
    void markOneReadThenAllRead() throws Exception {
        Customer alice = data.customer(spec());
        Long first = notification(alice);
        notification(alice);
        notification(alice);

        unreadCount(alice).andExpect(jsonPath("$.count").value(3));

        mockMvc.perform(put("/api/plus/notifications/" + first + "/read").with(user(alice.login()))).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/plus/notifications/" + first + "/read").with(user(alice.login()))).andExpect(status().isNoContent()); // idempotent
        unreadCount(alice).andExpect(jsonPath("$.count").value(2));
        mockMvc
            .perform(get("/api/plus/notifications").with(user(alice.login())))
            .andExpect(jsonPath("$[?(@.id == " + first + ")].isRead").value(true));

        mockMvc
            .perform(put("/api/plus/notifications/read-all").with(user(alice.login())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(2));
        unreadCount(alice).andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(put("/api/plus/notifications/read-all").with(user(alice.login()))).andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    void anotherUsersNotificationIsNotFoundAndStaysUnread() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        Long bobs = notification(bob);

        mockMvc
            .perform(put("/api/plus/notifications/" + bobs + "/read").with(user(alice.login())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.notificationnotfound"));
        mockMvc.perform(put("/api/plus/notifications/" + Long.MAX_VALUE + "/read").with(user(alice.login()))).andExpect(status().isNotFound());

        // read-all only touches the caller's own notifications
        mockMvc.perform(put("/api/plus/notifications/read-all").with(user(alice.login()))).andExpect(jsonPath("$.updated").value(0));
        unreadCount(bob).andExpect(jsonPath("$.count").value(1));
    }

    // ------------------------------------------------------------------------------------------ push token

    @Test
    void pushTokenCanBeSetReplacedAndCleared() throws Exception {
        Customer alice = data.customer(spec().pushToken("ExponentPushToken[old]"));

        pushToken(alice, "ExponentPushToken[new-1_A]").andExpect(status().isNoContent());
        assertThat(token(alice)).isEqualTo("ExponentPushToken[new-1_A]");

        pushToken(alice, null).andExpect(status().isNoContent());
        assertThat(token(alice)).isNull();
    }

    @Test
    void aTokenMovesToTheUserWhoRegistersIt() throws Exception {
        // same phone, new account: alice must no longer receive pushes on bob's phone
        Customer alice = data.customer(spec().pushToken("ExponentPushToken[shared-phone]"));
        Customer bob = data.customer(spec());

        pushToken(bob, "ExponentPushToken[shared-phone]").andExpect(status().isNoContent());

        assertThat(token(bob)).isEqualTo("ExponentPushToken[shared-phone]");
        assertThat(token(alice)).isNull();
    }

    @Test
    void invalidTokensAndUsersWithoutAccountAreRejected() throws Exception {
        Customer alice = data.customer(spec().pushToken("ExponentPushToken[keep]"));
        for (String bad : new String[] { "", "not-a-token", "ExponentPushToken[]", "ExponentPushToken[a b]", "https://evil/x" }) {
            pushToken(alice, bad).andExpect(status().isBadRequest());
        }
        assertThat(token(alice)).isEqualTo("ExponentPushToken[keep]");

        var noWallet = data.userWithoutAccount();
        mockMvc
            .perform(
                put("/api/plus/push-token")
                    .with(user(noWallet.getLogin()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expoPushToken\":\"ExponentPushToken[x]\"}")
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("error.noaccount"));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions unreadCount(Customer c) throws Exception {
        return mockMvc.perform(get("/api/plus/notifications/unread-count").with(user(c.login()))).andExpect(status().isOk());
    }

    private ResultActions pushToken(Customer c, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("expoPushToken", token);
        return mockMvc.perform(
            put("/api/plus/push-token").with(user(c.login())).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))
        );
    }

    private String token(Customer c) {
        return jdbcTemplate.queryForObject("select expo_push_token from user_details_account where user_id = ?", String.class, c.user().getId());
    }

    private Long notification(Customer c) {
        return new TransactionTemplate(transactionManager).execute(s -> {
            Long id = jdbcTemplate.queryForObject("select nextval('sequence_generator')", Long.class);
            jdbcTemplate.update(
                "insert into notification (id, title, message, type, channel, status, is_read, created_at, user_id) " +
                "values (?, 'Test', 'Test message', 'SYSTEM', 'PUSH', 'SENT', false, now(), ?)",
                id,
                c.user().getId()
            );
            return id;
        });
    }
}
