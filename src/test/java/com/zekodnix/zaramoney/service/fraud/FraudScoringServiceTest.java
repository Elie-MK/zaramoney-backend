package com.zekodnix.zaramoney.service.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Fraud.Mode;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionPin;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.UserSession;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.repository.TransactionPinRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserSessionRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Each fraud rule in isolation (repositories mocked), thresholds, hard rules, monitor vs enforce. */
class FraudScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final Long USER_ID = 7L;

    private final ApplicationProperties props = new ApplicationProperties();
    private final TransactionRecordRepository txRepo = mock(TransactionRecordRepository.class);
    private final UserSessionRepository sessionRepo = mock(UserSessionRepository.class);
    private final TransactionPinRepository pinRepo = mock(TransactionPinRepository.class);
    private final UserDetailsAccountRepository detailsRepo = mock(UserDetailsAccountRepository.class);
    private FraudScoringService service;
    private BankAccount sender;
    private BankAccount receiver;

    @BeforeEach
    void setUp() {
        service = new FraudScoringService(props, txRepo, sessionRepo, pinRepo, detailsRepo, new SimpleMeterRegistry());
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        sender = account(1L, "251200000001", "1000.00", NOW.minus(Duration.ofDays(200)));
        receiver = account(2L, "251200000002", "50.00", NOW.minus(Duration.ofDays(200)));
        receiver.setUser(new User());
        receiver.getUser().setId(99L);
        // quiet history by default
        lenient().when(txRepo.countOutgoingSince(anyLong(), any(), any())).thenReturn(0L);
        lenient().when(txRepo.countAndSumOutgoingSince(anyLong(), any(), any(), any())).thenReturn(List.<Object[]>of(new Object[] { 0L, BigDecimal.ZERO }));
        lenient().when(txRepo.countDistinctReceiversSince(anyLong(), anyLong(), any(), any(), any())).thenReturn(0L);
        lenient().when(txRepo.countDistinctSendersSince(anyLong(), anyLong(), any(), any(), any())).thenReturn(0L);
        lenient().when(pinRepo.findOneByUserId(USER_ID)).thenReturn(Optional.empty());
        lenient().when(detailsRepo.findOneByUser_Id(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private FraudAssessment transfer(String amount) {
        return service.assess(TransactionType.TRANSFER, sender, receiver, new BigDecimal(amount), BigDecimal.ZERO, USER_ID);
    }

    @Test
    void ordinaryPaymentIsClean() {
        FraudAssessment a = transfer("20.00");
        assertThat(a.score()).isZero();
        assertThat(a.status()).isEqualTo(FraudStatus.CLEAN);
        assertThat(a.reasons()).isEmpty();
        assertThat(a.reasonsText()).isNull();
        assertThat(a.reject()).isFalse();
    }

    @Test
    void burstAddsPointsButAloneStaysClean() {
        // 5 earlier attempts in 10 min + this one = 6 > 5
        lenient().when(txRepo.countOutgoingSince(eq(1L), any(), eq(NOW.minus(Duration.ofMinutes(10))))).thenReturn(5L);
        FraudAssessment a = transfer("20.00");
        assertThat(a.score()).isEqualTo(30);
        assertThat(a.status()).isEqualTo(FraudStatus.CLEAN);
        assertThat(a.reasons()).containsExactly("BURST:6/PT10M");
    }

    @Test
    void drainingTheWalletOnANewAccountIsSuspicious() {
        sender = account(1L, "251200000001", "160.00", NOW.minus(Duration.ofHours(2)));
        FraudAssessment a = service.assess(TransactionType.TRANSFER, sender, receiver, new BigDecimal("150.00"), new BigDecimal("1.43"), USER_ID);
        assertThat(a.reasons()).containsExactly("DRAIN", "NEW_ACCOUNT");
        assertThat(a.score()).isEqualTo(45);
        assertThat(a.status()).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    void smallAmountsDoNotTriggerTheAmountRules() {
        sender = account(1L, "251200000001", "40.00", NOW.minus(Duration.ofHours(2))); // new account
        FraudAssessment a = transfer("39.00"); // drains, but <= 50; new account, but <= 100
        assertThat(a.reasons()).isEmpty();
    }

    @Test
    void largePaymentRightAfterAPinChangeAddsPoints() {
        TransactionPin pin = new TransactionPin();
        pin.setUpdatedAt(NOW.minus(Duration.ofHours(3)));
        lenient().when(pinRepo.findOneByUserId(USER_ID)).thenReturn(Optional.of(pin));
        assertThat(transfer("150.00").reasons()).containsExactly("PIN_CHANGED");
        pin.setUpdatedAt(NOW.minus(Duration.ofDays(2)));
        assertThat(transfer("150.00").reasons()).isEmpty();
    }

    @Test
    void unusualAmountComparesWithTheUsersOwnAverage() {
        // 4 payments totalling 200 in 30 days → average 50; 3 x 50 = 150 < 250
        lenient().when(txRepo.countAndSumOutgoingSince(eq(1L), any(), any(), eq(NOW.minus(Duration.ofDays(30)))))
            .thenReturn(List.<Object[]>of(new Object[] { 4L, new BigDecimal("200.00") }));
        assertThat(transfer("250.00").reasons()).containsExactly("UNUSUAL_AMOUNT:avg=50.00");
        assertThat(transfer("150.00").reasons()).isEmpty(); // not > 3x
        lenient().when(txRepo.countAndSumOutgoingSince(eq(1L), any(), any(), eq(NOW.minus(Duration.ofDays(30)))))
            .thenReturn(List.<Object[]>of(new Object[] { 2L, new BigDecimal("20.00") }));
        assertThat(transfer("250.00").reasons()).isEmpty(); // not enough history
    }

    @Test
    void fanOutAndMuleCountDistinctCounterparties() {
        lenient().when(txRepo.countDistinctReceiversSince(eq(1L), eq(2L), any(), any(), any())).thenReturn(9L);
        lenient().when(txRepo.countDistinctSendersSince(eq(2L), eq(1L), any(), any(), any())).thenReturn(9L);
        FraudAssessment a = transfer("20.00");
        assertThat(a.reasons()).containsExactly("FAN_OUT:10", "MULE:10");
        assertThat(a.score()).isEqualTo(40);
        assertThat(a.status()).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    void agentsAreNotMules() {
        UserDetailsAccount agent = new UserDetailsAccount();
        agent.setIsAgent(true);
        lenient().when(detailsRepo.findOneByUser_Id(99L)).thenReturn(Optional.of(agent));
        lenient().when(txRepo.countDistinctSendersSince(eq(2L), eq(1L), any(), any(), any())).thenReturn(50L);
        assertThat(transfer("20.00").reasons()).isEmpty();
        // and withdrawals never count fan-out / mule
        assertThat(service.assess(TransactionType.WITHDRAWAL, sender, receiver, new BigDecimal("20"), BigDecimal.ZERO, USER_ID).reasons()).isEmpty();
    }

    @Test
    void largePaymentFromANewDeviceAddsPointsAndAVeryLargeOneIsBlocked() {
        loginWithSession("sid-1");
        UserSession session = new UserSession();
        session.setDeviceId("device-new");
        lenient().when(sessionRepo.findOneBySid("sid-1")).thenReturn(Optional.of(session));
        Instant firstSeen = NOW.minus(Duration.ofMinutes(20));
        lenient().when(sessionRepo.findFirstSeen(USER_ID, "device-new")).thenReturn(Optional.of(firstSeen));

        assertThat(transfer("150.00").reasons()).containsExactly("NEW_DEVICE");

        // 1,900 already sent from this device + 150 > 2,000: hard rule
        lenient().when(txRepo.countAndSumOutgoingSince(eq(1L), any(), any(), eq(firstSeen)))
            .thenReturn(List.<Object[]>of(new Object[] { 3L, new BigDecimal("1900.00") }));
        FraudAssessment a = transfer("150.00");
        assertThat(a.reasons()).contains("NEW_DEVICE", "NEW_DEVICE_LARGE:2050.00");
        assertThat(a.status()).isEqualTo(FraudStatus.BLOCKED);
        assertThat(a.score()).isGreaterThanOrEqualTo(70);

        // a device seen long ago is not new
        lenient().when(sessionRepo.findFirstSeen(USER_ID, "device-new")).thenReturn(Optional.of(NOW.minus(Duration.ofDays(3))));
        assertThat(transfer("150.00").reasons()).isEmpty();
    }

    @Test
    void tooManyPaymentsInAnHourIsBlockedOnlyInEnforceMode() {
        lenient().when(txRepo.countOutgoingSince(eq(1L), any(), eq(NOW.minus(Duration.ofHours(1))))).thenReturn(15L); // + this = 16
        FraudAssessment monitor = transfer("5.00");
        assertThat(monitor.status()).isEqualTo(FraudStatus.BLOCKED);
        assertThat(monitor.reasons()).contains("HOURLY_LIMIT:16/PT1H");
        assertThat(monitor.reject()).as("monitor mode never rejects").isFalse();

        props.getFraud().setMode(Mode.ENFORCE);
        assertThat(transfer("5.00").reject()).isTrue();
    }

    @Test
    void scoreAtTheBlockThresholdBlocksAndIsCapped() {
        sender = account(1L, "251200000001", "160.00", NOW.minus(Duration.ofHours(2))); // DRAIN + NEW_ACCOUNT = 45
        lenient().when(txRepo.countOutgoingSince(eq(1L), any(), eq(NOW.minus(Duration.ofMinutes(10))))).thenReturn(5L); // BURST 30
        FraudAssessment a = service.assess(TransactionType.TRANSFER, sender, receiver, new BigDecimal("150.00"), new BigDecimal("1.43"), USER_ID);
        assertThat(a.score()).isEqualTo(75);
        assertThat(a.status()).isEqualTo(FraudStatus.BLOCKED);

        props.getFraud().getBurst().setPoints(90);
        assertThat(service.assess(TransactionType.TRANSFER, sender, receiver, new BigDecimal("150.00"), new BigDecimal("1.43"), USER_ID).score())
            .isEqualTo(100);
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static BankAccount account(Long id, String number, String balance, Instant createdAt) {
        BankAccount a = new BankAccount().accountNumber(number).balance(new BigDecimal(balance)).createdAt(createdAt);
        a.setId(id);
        return a;
    }

    private static void loginWithSession(String sid) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS512").subject("alice").claim(SecurityUtils.SESSION_ID_CLAIM, sid).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
