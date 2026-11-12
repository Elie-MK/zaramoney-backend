package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.ExpoPushService;
import com.zekodnix.zaramoney.service.NotificationServicePlus;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.TransactionRecordService;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import reactor.core.publisher.Mono;
import com.zekodnix.zaramoney.service.push.PushResult;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Expo pushes are sent only after the transaction commits (never for a rolled-back transaction). */
@IntegrationTest
@AutoConfigureMockMvc
class PushAfterCommitIT {

    @MockitoBean
    private ExpoPushService expoPushService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationServicePlus notificationServicePlus;

    @Autowired
    private TransactionRecordService transactionRecordService;

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
        reset(expoPushService);
        when(expoPushService.sendPush(any(), any(), any(), any())).thenReturn(Mono.just(PushResult.sent("ticket")));
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
    void aCompletedTransferPushesToBothSides() throws Exception {
        Customer alice = data.customer(spec().pushToken("ExponentPushToken[alice]"));
        Customer bob = data.customer(spec().pushToken("ExponentPushToken[bob]"));

        send(alice, bob);

        verify(expoPushService).sendPush(eq("ExponentPushToken[alice]"), anyString(), anyString(), anyMap());
        verify(expoPushService).sendPush(eq("ExponentPushToken[bob]"), anyString(), anyString(), anyMap());
    }

    @Test
    void noPushWhenTheTransactionRollsBack() throws Exception {
        // a real committed transaction to attach the notifications to; nobody has a token yet, so no push
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        TransactionRecordDTO transfer = send(alice, bob);
        verify(expoPushService, never()).sendPush(any(), any(), any(), any());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s ->
            jdbcTemplate.update("update user_details_account set expo_push_token = ? where user_id = ?", "ExponentPushToken[bob]", bob.user().getId())
        );

        tx.executeWithoutResult(status -> {
            notificationServicePlus.createTransactionNotification(transfer, bob.user(), new BigDecimal("10"), false);
            verify(expoPushService, never()).sendPush(any(), any(), any(), any()); // not before commit either
            status.setRollbackOnly();
        });
        verify(expoPushService, never()).sendPush(any(), any(), any(), any());

        tx.executeWithoutResult(status -> notificationServicePlus.createTransactionNotification(transfer, bob.user(), new BigDecimal("10"), false));
        verify(expoPushService, times(1)).sendPush(eq("ExponentPushToken[bob]"), anyString(), anyString(), anyMap());
    }

    @Test
    void userWithoutPushTokenIsSkippedNotFailed() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec());
        TransactionRecordDTO transfer = send(alice, bob);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            notificationServicePlus.createTransactionNotification(transfer, bob.user(), new BigDecimal("10"), false)
        );
        verify(expoPushService, never()).sendPush(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------------ delivery outcome

    @Test
    void anAcceptedPushMarksTheNotificationSent() throws Exception {
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec().pushToken("ExponentPushToken[bob]"));
        TransactionRecordDTO transfer = send(alice, bob);

        awaitStatus(bob, transfer, "SENT");
        assertThat(
            jdbcTemplate.queryForObject(
                "select sent_at is not null from notification where user_id = ? and transaction_id = ?",
                Boolean.class,
                bob.user().getId(),
                transfer.getId()
            )
        ).isTrue();
        // alice has no token: nothing was pushed, the notification is FAILED (still listed in the app)
        assertThat(notificationStatus(alice, transfer)).isEqualTo("FAILED");
    }

    @Test
    void anUnregisteredDeviceTokenIsRemovedAndTheNotificationFailed() throws Exception {
        when(expoPushService.sendPush(eq("ExponentPushToken[gone]"), any(), any(), any())).thenReturn(
            Mono.just(PushResult.invalidToken("DeviceNotRegistered"))
        );
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec().pushToken("ExponentPushToken[gone]"));
        TransactionRecordDTO transfer = send(alice, bob);

        awaitStatus(bob, transfer, "FAILED");
        assertThat(
            jdbcTemplate.queryForObject("select expo_push_token from user_details_account where user_id = ?", String.class, bob.user().getId())
        ).isNull();
    }

    @Test
    void aPushThatKeepsFailingIsRecordedAsFailed() throws Exception {
        when(expoPushService.sendPush(eq("ExponentPushToken[flaky]"), any(), any(), any())).thenReturn(Mono.just(PushResult.failed("HTTP 503")));
        Customer alice = data.customer(spec());
        Customer bob = data.customer(spec().pushToken("ExponentPushToken[flaky]"));
        TransactionRecordDTO transfer = send(alice, bob);

        awaitStatus(bob, transfer, "FAILED");
        // a transient failure keeps the token
        assertThat(
            jdbcTemplate.queryForObject("select expo_push_token from user_details_account where user_id = ?", String.class, bob.user().getId())
        ).isEqualTo("ExponentPushToken[flaky]");
    }

    private String notificationStatus(Customer c, TransactionRecordDTO transfer) {
        return jdbcTemplate.queryForObject(
            "select status from notification where user_id = ? and transaction_id = ?",
            String.class,
            c.user().getId(),
            transfer.getId()
        );
    }

    /** The outcome is recorded asynchronously (after the Expo call): poll for up to 5 s. */
    private void awaitStatus(Customer c, TransactionRecordDTO transfer, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        String current = notificationStatus(c, transfer);
        while (!expected.equals(current) && System.nanoTime() < deadline) {
            Thread.sleep(50);
            current = notificationStatus(c, transfer);
        }
        assertThat(current).isEqualTo(expected);
    }

    private TransactionRecordDTO send(Customer from, Customer to) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverAccountNumber", to.accountNumber());
        body.put("sendAmount", new BigDecimal("10"));
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
        long id = objectMapper.readTree(json).get("id").asLong();
        return transactionRecordService.findOne(id).orElseThrow();
    }
}
