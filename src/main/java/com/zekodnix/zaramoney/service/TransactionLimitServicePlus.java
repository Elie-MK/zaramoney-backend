package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionLimitServicePlus {

    public static final BigDecimal DEFAULT_DAILY_LIMIT = BigDecimal.valueOf(1000);
    public static final BigDecimal DEFAULT_MONTHLY_LIMIT = BigDecimal.valueOf(10000);

    /** Days and months are counted in Kinshasa time. */
    public static final ZoneId LIMITS_ZONE = ZoneId.of("Africa/Kinshasa");

    private final TransactionLimitService transactionLimitService;
    private final TransactionLimitRepository transactionLimitRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private Clock clock = Clock.systemUTC();

    public TransactionLimitServicePlus(
        TransactionLimitService transactionLimitService,
        TransactionLimitRepository transactionLimitRepository,
        TransactionRecordRepository transactionRecordRepository
    ) {
        this.transactionLimitService = transactionLimitService;
        this.transactionLimitRepository = transactionLimitRepository;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    /** For tests. */
    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public void createBankAccountLimit(BankAccountDTO account) {
        var transactionLimit = new TransactionLimitDTO();
        transactionLimit.setDailyLimit(DEFAULT_DAILY_LIMIT);
        transactionLimit.setMonthlyLimit(DEFAULT_MONTHLY_LIMIT);
        transactionLimit.setAccount(account);
        transactionLimitService.save(transactionLimit);
    }

    /**
     * Rejects the debit if it would exceed the daily or monthly limit of the account.
     * Call it while the sender account row is locked, so parallel transfers cannot both pass the check.
     */
    public void checkLimits(BankAccount sender, BigDecimal amount) {
        var limit = transactionLimitRepository.findOneByAccountId(sender.getId());
        BigDecimal daily = limit.map(l -> l.getDailyLimit()).orElse(DEFAULT_DAILY_LIMIT);
        BigDecimal monthly = limit.map(l -> l.getMonthlyLimit()).orElse(DEFAULT_MONTHLY_LIMIT);

        LocalDate today = LocalDate.now(clock.withZone(LIMITS_ZONE));
        Instant startOfDay = today.atStartOfDay(LIMITS_ZONE).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(LIMITS_ZONE).toInstant();

        BigDecimal spentToday = transactionRecordRepository.sumOutgoingSince(sender.getId(), TransactionStatus.COMPLETED, startOfDay);
        if (spentToday.add(amount).compareTo(daily) > 0) {
            throw ApiException.badRequest(
                "Daily limit exceeded: " + spentToday + " already sent today, limit is " + daily,
                "dailylimitexceeded"
            );
        }

        BigDecimal spentThisMonth = transactionRecordRepository.sumOutgoingSince(
            sender.getId(),
            TransactionStatus.COMPLETED,
            startOfMonth
        );
        if (spentThisMonth.add(amount).compareTo(monthly) > 0) {
            throw ApiException.badRequest(
                "Monthly limit exceeded: " + spentThisMonth + " already sent this month, limit is " + monthly,
                "monthlylimitexceeded"
            );
        }
    }
}
