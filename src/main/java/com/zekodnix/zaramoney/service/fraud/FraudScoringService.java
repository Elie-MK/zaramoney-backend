package com.zekodnix.zaramoney.service.fraud;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Fraud;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.repository.TransactionPinRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserSessionRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Rule-based fraud scoring of customer payments (transfers and withdrawals). Runs inside the money transaction,
 * AFTER the account rows are locked (so the velocity counts of one sender are consistent) and BEFORE money moves.
 *
 * <p>Score = sum of the points of the rules that fired, capped at 100 ({@code application.fraud}, defaults in
 * {@link ApplicationProperties.Fraud}):
 * <ul>
 *   <li>BURST: more than N outgoing payment attempts in a short window</li>
 *   <li>DRAIN: amount + fee takes ≥ 90 % of the balance</li>
 *   <li>NEW_ACCOUNT / NEW_DEVICE / PIN_CHANGED: large payment right after the account, the device or the PIN is new
 *       (account-takeover pattern)</li>
 *   <li>UNUSUAL_AMOUNT: well above the user's own 30-day average</li>
 *   <li>FAN_OUT: many different receivers; MULE: the receiver is paid by many different senders (not agents)</li>
 *   <li>hard rules (BLOCKED whatever the score): HOURLY_LIMIT (too many payments in an hour) and NEW_DEVICE_LARGE
 *       (too much sent within the first hour on a new device)</li>
 * </ul>
 * Status: score ≥ block-score or a hard rule → BLOCKED; ≥ suspicious-score → SUSPICIOUS; else CLEAN. In MONITOR mode
 * nothing is rejected (BLOCKED + COMPLETED means "would have been blocked").
 */
@Service
public class FraudScoringService {

    private static final Logger LOG = LoggerFactory.getLogger(FraudScoringService.class);
    private static final Set<TransactionType> PAYMENTS = Set.of(TransactionType.TRANSFER, TransactionType.WITHDRAWAL);

    private final Fraud config;
    private final TransactionRecordRepository transactionRecordRepository;
    private final UserSessionRepository userSessionRepository;
    private final TransactionPinRepository transactionPinRepository;
    private final UserDetailsAccountRepository userDetailsAccountRepository;
    private final MeterRegistry meterRegistry;
    private Clock clock = Clock.systemUTC();

    public FraudScoringService(
        ApplicationProperties properties,
        TransactionRecordRepository transactionRecordRepository,
        UserSessionRepository userSessionRepository,
        TransactionPinRepository transactionPinRepository,
        UserDetailsAccountRepository userDetailsAccountRepository,
        MeterRegistry meterRegistry
    ) {
        this.config = properties.getFraud();
        this.transactionRecordRepository = transactionRecordRepository;
        this.userSessionRepository = userSessionRepository;
        this.transactionPinRepository = transactionPinRepository;
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.meterRegistry = meterRegistry;
    }

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param sender   the LOCKED sender account (balance before this payment)
     * @param receiver the LOCKED receiver / agent account
     * @param userId   the paying user
     */
    public FraudAssessment assess(TransactionType type, BankAccount sender, BankAccount receiver, BigDecimal amount, BigDecimal fee, Long userId) {
        Instant now = clock.instant();
        List<String> reasons = new ArrayList<>();
        int score = 0;
        boolean hard = false;

        // --- velocity (attempts of any status: retried blocked payments count too)
        long lastHour = transactionRecordRepository.countOutgoingSince(sender.getId(), PAYMENTS, now.minus(config.getHourlyHardLimit().getWindow())) + 1;
        if (lastHour > config.getHourlyHardLimit().getMaxCount()) {
            hard = true;
            reasons.add("HOURLY_LIMIT:" + lastHour + "/" + config.getHourlyHardLimit().getWindow());
        }
        long burst = transactionRecordRepository.countOutgoingSince(sender.getId(), PAYMENTS, now.minus(config.getBurst().getWindow())) + 1;
        if (burst > config.getBurst().getMaxCount()) {
            score += config.getBurst().getPoints();
            reasons.add("BURST:" + burst + "/" + config.getBurst().getWindow());
        }

        // --- drains the wallet
        BigDecimal total = amount.add(fee);
        if (
            amount.compareTo(config.getDrain().getMinAmount()) > 0 &&
            sender.getBalance().signum() > 0 &&
            total.compareTo(sender.getBalance().multiply(config.getDrain().getRatio())) >= 0
        ) {
            score += config.getDrain().getPoints();
            reasons.add("DRAIN");
        }

        // --- new account
        if (isRecent(sender.getCreatedAt(), config.getNewAccount().getAge(), now) && above(amount, config.getNewAccount().getMinAmount())) {
            score += config.getNewAccount().getPoints();
            reasons.add("NEW_ACCOUNT");
        }

        // --- new device (only for device-bound sessions: the JWT carries the session id)
        Optional<Instant> deviceFirstSeen = currentDeviceFirstSeen(userId);
        boolean newDevice = deviceFirstSeen.map(t -> isRecent(t, config.getNewDevice().getAge(), now)).orElse(false);
        if (newDevice && above(amount, config.getNewDevice().getMinAmount())) {
            score += config.getNewDevice().getPoints();
            reasons.add("NEW_DEVICE");
        }
        if (newDevice) {
            BigDecimal sentOnDevice = completedSince(sender, deviceFirstSeen.orElseThrow())[1].add(amount);
            if (sentOnDevice.compareTo(config.getNewDeviceHardBlockAmount()) > 0) {
                hard = true;
                reasons.add("NEW_DEVICE_LARGE:" + sentOnDevice.setScale(2, RoundingMode.HALF_UP));
            }
        }

        // --- PIN just set / changed
        boolean pinChanged = transactionPinRepository
            .findOneByUserId(userId)
            .map(pin -> isRecent(pin.getUpdatedAt(), config.getPinChanged().getAge(), now))
            .orElse(false);
        if (pinChanged && above(amount, config.getPinChanged().getMinAmount())) {
            score += config.getPinChanged().getPoints();
            reasons.add("PIN_CHANGED");
        }

        // --- unusual amount for this user
        var unusual = config.getUnusualAmount();
        if (above(amount, unusual.getMinAmount())) {
            BigDecimal[] history = completedSince(sender, now.minus(unusual.getLookback()));
            long count = history[0].longValue();
            if (count >= unusual.getMinHistory()) {
                BigDecimal average = history[1].divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                if (amount.compareTo(average.multiply(unusual.getMultiplier())) > 0) {
                    score += unusual.getPoints();
                    reasons.add("UNUSUAL_AMOUNT:avg=" + average);
                }
            }
        }

        // --- fan-out (transfers): many different receivers
        if (type == TransactionType.TRANSFER) {
            long receivers =
                transactionRecordRepository.countDistinctReceiversSince(
                    sender.getId(),
                    receiver.getId(),
                    TransactionType.TRANSFER,
                    TransactionStatus.COMPLETED,
                    now.minus(config.getFanOut().getWindow())
                ) +
                1;
            if (receivers >= config.getFanOut().getMaxCount()) {
                score += config.getFanOut().getPoints();
                reasons.add("FAN_OUT:" + receivers);
            }

            // --- mule (transfers to a non-agent): many different senders pay the same account
            long senders =
                transactionRecordRepository.countDistinctSendersSince(
                    receiver.getId(),
                    sender.getId(),
                    TransactionType.TRANSFER,
                    TransactionStatus.COMPLETED,
                    now.minus(config.getMule().getWindow())
                ) +
                1;
            if (senders >= config.getMule().getMaxCount() && !isAgent(receiver)) {
                score += config.getMule().getPoints();
                reasons.add("MULE:" + senders);
            }
        }

        score = Math.min(score, 100);
        FraudStatus status = hard || score >= config.getBlockScore()
            ? FraudStatus.BLOCKED
            : score >= config.getSuspiciousScore() ? FraudStatus.SUSPICIOUS : FraudStatus.CLEAN;
        if (hard) {
            score = Math.max(score, config.getBlockScore());
        }
        boolean reject = status == FraudStatus.BLOCKED && config.getMode() == Fraud.Mode.ENFORCE;

        meterRegistry.counter("zaramoney.fraud", "status", status.name().toLowerCase(), "mode", config.getMode().name().toLowerCase()).increment();
        if (status != FraudStatus.CLEAN) {
            LOG.warn(
                "FRAUD {} score={} mode={} account={} type={} amount={} reasons={}",
                status,
                score,
                config.getMode(),
                sender.getAccountNumber(),
                type,
                amount,
                reasons
            );
        }
        return new FraudAssessment(score, status, List.copyOf(reasons), reject);
    }

    /** [count, sum] of completed payments sent since {@code from}. */
    private BigDecimal[] completedSince(BankAccount sender, Instant from) {
        List<Object[]> rows = transactionRecordRepository.countAndSumOutgoingSince(sender.getId(), PAYMENTS, TransactionStatus.COMPLETED, from);
        Object[] row = rows.isEmpty() ? new Object[] { 0L, BigDecimal.ZERO } : rows.get(0);
        return new BigDecimal[] { BigDecimal.valueOf(((Number) row[0]).longValue()), (BigDecimal) row[1] };
    }

    private Optional<Instant> currentDeviceFirstSeen(Long userId) {
        return SecurityUtils.getCurrentSessionId()
            .flatMap(userSessionRepository::findOneBySid)
            .map(s -> s.getDeviceId())
            .filter(deviceId -> !deviceId.isBlank())
            .flatMap(deviceId -> userSessionRepository.findFirstSeen(userId, deviceId));
    }

    private boolean isAgent(BankAccount account) {
        return account.getUser() != null &&
            userDetailsAccountRepository.findOneByUser_Id(account.getUser().getId()).map(UserDetailsAccount::getIsAgent).orElse(false);
    }

    private static boolean isRecent(Instant time, Duration age, Instant now) {
        return time != null && time.isAfter(now.minus(age));
    }

    private static boolean above(BigDecimal amount, BigDecimal min) {
        return amount.compareTo(min) > 0;
    }
}
