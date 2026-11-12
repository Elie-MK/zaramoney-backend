package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.RevenueReportDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Income report for the back office, computed from the ledger of the SYSTEM accounts (see {@link RevenueReportDTO}).
 * Periods are calendar days / months / years in Kinshasa time, the same zone as the transaction limits.
 * Timestamps are stored as UTC in {@code timestamp without time zone} columns ({@code hibernate.jdbc.time_zone: UTC}).
 */
@Service
@Transactional(readOnly = true)
public class RevenueReportService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    private static final int MAX_PERIODS = 1000;

    public enum GroupBy {
        DAY("day", "YYYY-MM-DD", DateTimeFormatter.ofPattern("yyyy-MM-dd"), ChronoUnit.DAYS),
        MONTH("month", "YYYY-MM", DateTimeFormatter.ofPattern("yyyy-MM"), ChronoUnit.MONTHS),
        YEAR("year", "YYYY", DateTimeFormatter.ofPattern("yyyy"), ChronoUnit.YEARS);

        final String sqlUnit;
        final String sqlFormat;
        final DateTimeFormatter label;
        final ChronoUnit step;

        GroupBy(String sqlUnit, String sqlFormat, DateTimeFormatter label, ChronoUnit step) {
            this.sqlUnit = sqlUnit;
            this.sqlFormat = sqlFormat;
            this.label = label;
            this.step = step;
        }

        public static GroupBy parse(String value) {
            try {
                return GroupBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                throw ApiException.badRequest("groupBy must be day, month or year", "invalidgroupby");
            }
        }

        LocalDate startOf(LocalDate date) {
            return switch (this) {
                case DAY -> date;
                case MONTH -> date.withDayOfMonth(1);
                case YEAR -> date.withDayOfYear(1);
            };
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public RevenueReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param from first day (inclusive), @param to last day (inclusive), both in Kinshasa time */
    public RevenueReportDTO report(LocalDate from, LocalDate to, GroupBy groupBy) {
        if (from == null || to == null || from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be on or before 'to'", "invaliddaterange");
        }
        if (groupBy.step.between(groupBy.startOf(from), groupBy.startOf(to)) >= MAX_PERIODS) {
            throw ApiException.badRequest("Too many periods, use a larger groupBy or a shorter range", "invaliddaterange");
        }
        // [from 00:00 Kinshasa, to+1 00:00 Kinshasa) as UTC wall-clock values, matching the stored columns
        LocalDateTime start = LocalDateTime.ofInstant(from.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(to.plusDays(1).atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC);
        // groupBy values come from the enum above, never from user input, so inlining them is safe
        String period =
            "to_char(date_trunc('" +
            groupBy.sqlUnit +
            "', (t.transaction_date at time zone 'UTC') at time zone '" +
            ZONE.getId() +
            "'), '" +
            groupBy.sqlFormat +
            "')";

        Map<String, Acc> byPeriod = new LinkedHashMap<>();
        for (LocalDate d = groupBy.startOf(from); !d.isAfter(to); d = d.plus(1, groupBy.step)) {
            byPeriod.put(groupBy.label.format(d), new Acc());
        }

        // 1. Ledger of the system accounts: fees, bonuses, deposits
        jdbcTemplate.query(
            "select " +
            period +
            " as period, a.account_number, l.entry_type, t.transaction_type, count(distinct t.id) as n, sum(l.amount) as total " +
            "from ledger_entry l join bank_account a on a.id = l.account_id join transaction_record t on t.id = l.transaction_id " +
            "where a.account_number in (?, ?, ?) and t.transaction_status = 'COMPLETED' " +
            "and t.transaction_date >= ? and t.transaction_date < ? group by 1, 2, 3, 4",
            rs -> {
                Acc acc = byPeriod.computeIfAbsent(rs.getString("period"), k -> new Acc());
                String account = rs.getString("account_number");
                boolean credit = "CREDIT".equals(rs.getString("entry_type"));
                String type = rs.getString("transaction_type");
                long n = rs.getLong("n");
                BigDecimal total = rs.getBigDecimal("total");
                if (SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER.equals(account)) {
                    BigDecimal signed = credit ? total : total.negate(); // a DEBIT on the fee account = fee refunded
                    if ("WITHDRAWAL".equals(type)) {
                        acc.withdrawalFees = acc.withdrawalFees.add(signed);
                    } else {
                        acc.transferFees = acc.transferFees.add(signed);
                    }
                    if (credit) {
                        acc.feeTransactions += n;
                    }
                } else if (SystemAccountService.PROMO_ACCOUNT_NUMBER.equals(account)) {
                    acc.bonusCost = acc.bonusCost.add(credit ? total.negate() : total);
                    if (!credit) {
                        acc.bonusCount += n;
                    }
                } else if (SystemAccountService.SETTLEMENT_ACCOUNT_NUMBER.equals(account) && !credit) {
                    acc.deposits = acc.deposits.add(total);
                    acc.depositCount += n;
                }
            },
            SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER,
            SystemAccountService.PROMO_ACCOUNT_NUMBER,
            SystemAccountService.SETTLEMENT_ACCOUNT_NUMBER,
            start,
            end
        );

        // 2. Activity volumes (customer side)
        jdbcTemplate.query(
            "select " +
            period +
            " as period, t.transaction_type, count(*) as n, sum(t.send_amount) as total from transaction_record t " +
            "where t.transaction_status = 'COMPLETED' and t.transaction_type in ('TRANSFER', 'WITHDRAWAL') " +
            "and t.transaction_date >= ? and t.transaction_date < ? group by 1, 2",
            rs -> {
                Acc acc = byPeriod.computeIfAbsent(rs.getString("period"), k -> new Acc());
                if ("WITHDRAWAL".equals(rs.getString("transaction_type"))) {
                    acc.withdrawalCount += rs.getLong("n");
                    acc.withdrawalVolume = acc.withdrawalVolume.add(rs.getBigDecimal("total"));
                } else {
                    acc.transferCount += rs.getLong("n");
                    acc.transferVolume = acc.transferVolume.add(rs.getBigDecimal("total"));
                }
            },
            start,
            end
        );

        List<RevenueReportDTO.Period> periods = new ArrayList<>();
        Acc totals = new Acc();
        byPeriod.forEach((label, acc) -> {
            periods.add(acc.toPeriod(label));
            totals.add(acc);
        });

        BigDecimal feeBalance = jdbcTemplate.queryForObject(
            "select balance from bank_account where account_number = ?",
            BigDecimal.class,
            SystemAccountService.FEE_REVENUE_ACCOUNT_NUMBER
        );

        return new RevenueReportDTO(
            from,
            to,
            groupBy.name().toLowerCase(Locale.ROOT),
            "USD",
            ZONE.getId(),
            periods,
            totals.toPeriod("total"),
            feeBalance
        );
    }

    /** Mutable accumulator for one period. */
    private static final class Acc {

        BigDecimal transferFees = BigDecimal.ZERO;
        BigDecimal withdrawalFees = BigDecimal.ZERO;
        long feeTransactions;
        long transferCount;
        BigDecimal transferVolume = BigDecimal.ZERO;
        long withdrawalCount;
        BigDecimal withdrawalVolume = BigDecimal.ZERO;
        BigDecimal bonusCost = BigDecimal.ZERO;
        long bonusCount;
        BigDecimal deposits = BigDecimal.ZERO;
        long depositCount;

        void add(Acc o) {
            transferFees = transferFees.add(o.transferFees);
            withdrawalFees = withdrawalFees.add(o.withdrawalFees);
            feeTransactions += o.feeTransactions;
            transferCount += o.transferCount;
            transferVolume = transferVolume.add(o.transferVolume);
            withdrawalCount += o.withdrawalCount;
            withdrawalVolume = withdrawalVolume.add(o.withdrawalVolume);
            bonusCost = bonusCost.add(o.bonusCost);
            bonusCount += o.bonusCount;
            deposits = deposits.add(o.deposits);
            depositCount += o.depositCount;
        }

        RevenueReportDTO.Period toPeriod(String label) {
            BigDecimal totalFees = transferFees.add(withdrawalFees);
            return new RevenueReportDTO.Period(
                label,
                scale(transferFees),
                scale(withdrawalFees),
                scale(totalFees),
                feeTransactions,
                transferCount,
                scale(transferVolume),
                withdrawalCount,
                scale(withdrawalVolume),
                scale(bonusCost),
                bonusCount,
                scale(deposits),
                depositCount,
                scale(totalFees.subtract(bonusCost))
            );
        }

        private static BigDecimal scale(BigDecimal v) {
            return v.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
