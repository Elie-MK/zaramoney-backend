package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.web.rest.MoneyTestData.spec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.ExpoPushService;
import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.web.rest.MoneyTestData.Customer;
import java.util.Map;
import reactor.core.publisher.Mono;
import com.zekodnix.zaramoney.service.push.PushResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

/** The user is notified (stored notification + Expo push after commit) when an admin approves or rejects their KYC. */
@IntegrationTest
@AutoConfigureMockMvc
class KycDecisionNotificationIT {

    private static final String TOKEN = "ExponentPushToken[kyc]";

    @MockitoBean
    private ExpoPushService expoPushService;

    @Autowired
    private MockMvc mockMvc;

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
    void approvalStoresANotificationAndPushesOnce() throws Exception {
        Customer alice = data.customer(spec().kyc(KycStatus.PENDING).pushToken(TOKEN));

        decide(alice, "approve").andExpect(status().isOk()).andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        verify(expoPushService).sendPush(
            eq(TOKEN),
            eq("Compte vérifié"),
            anyString(),
            eq(Map.of("type", "KYC_STATUS", "kycStatus", "VERIFIED", "deepLink", "account"))
        );
        mockMvc
            .perform(get("/api/plus/notifications").with(user(alice.login())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("SYSTEM"))
            .andExpect(jsonPath("$[0].title").value("Compte vérifié"))
            .andExpect(jsonPath("$[0].deepLink").value("account"))
            .andExpect(jsonPath("$[0].isRead").value(false))
            .andExpect(jsonPath("$[0].data").value("{\"type\":\"KYC_STATUS\",\"kycStatus\":\"VERIFIED\"}"));

        // approving again changes nothing: no second notification, no second push
        decide(alice, "approve").andExpect(status().isOk());
        verify(expoPushService, times(1)).sendPush(any(), any(), any(), any());
        assertThat(notificationCount(alice)).isEqualTo(1);
    }

    @Test
    void rejectionIsNotifiedToo() throws Exception {
        Customer alice = data.customer(spec().kyc(KycStatus.PENDING).pushToken(TOKEN));

        decide(alice, "reject").andExpect(status().isOk()).andExpect(jsonPath("$.kycStatus").value("REJECTED"));

        verify(expoPushService).sendPush(
            eq(TOKEN),
            eq("Vérification refusée"),
            anyString(),
            eq(Map.of("type", "KYC_STATUS", "kycStatus", "REJECTED", "deepLink", "account"))
        );
        assertThat(notificationCount(alice)).isEqualTo(1);
    }

    @Test
    void withoutAPushTokenTheNotificationIsStillStored() throws Exception {
        Customer alice = data.customer(spec().kyc(KycStatus.PENDING));

        decide(alice, "approve").andExpect(status().isOk());

        verify(expoPushService, never()).sendPush(any(), any(), any(), any());
        assertThat(notificationCount(alice)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions decide(Customer c, String decision) throws Exception {
        return mockMvc.perform(post("/api/admin/backoffice/kyc/" + detailsId(c) + "/" + decision).with(asAdmin()));
    }

    private Long detailsId(Customer c) {
        return jdbcTemplate.queryForObject("select id from user_details_account where user_id = ?", Long.class, c.user().getId());
    }

    private int notificationCount(Customer c) {
        return jdbcTemplate.queryForObject("select count(*) from notification where user_id = ?", Integer.class, c.user().getId());
    }

    private RequestPostProcessor asAdmin() {
        return user("it-kyc-admin").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
