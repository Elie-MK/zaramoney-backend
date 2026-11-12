package com.zekodnix.zaramoney.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ZaraMoney income over a date range, per day / month / year (Kinshasa time), computed from the ledger:
 * <ul>
 *   <li>fees = net CREDITs on the SYSTEM fee revenue account (transfer and withdrawal fees)</li>
 *   <li>bonusCost = DEBITs on the SYSTEM promo account (welcome bonuses paid)</li>
 *   <li>netRevenue = totalFees - bonusCost</li>
 *   <li>volumes = completed transfers / withdrawals (count and amount), for context</li>
 *   <li>deposits = cash credited by admins (DEBITs on the settlement account). Not income: money owed to customers</li>
 * </ul>
 */
public record RevenueReportDTO(
    LocalDate from,
    LocalDate to,
    String groupBy,
    String currency,
    String timeZone,
    List<Period> periods,
    Period totals,
    /** All-time balance of the fee revenue account (total fees since launch, incl. the 2026-11 backfill). */
    BigDecimal feeRevenueAccountBalance
)
    implements Serializable {
    public record Period(
        /** "2026-11-12" (day), "2026-11" (month) or "2026" (year); "total" for the totals row. */
        String period,
        BigDecimal transferFees,
        BigDecimal withdrawalFees,
        BigDecimal totalFees,
        long feeTransactions,
        long transferCount,
        BigDecimal transferVolume,
        long withdrawalCount,
        BigDecimal withdrawalVolume,
        BigDecimal bonusCost,
        long bonusCount,
        BigDecimal deposits,
        long depositCount,
        BigDecimal netRevenue
    )
        implements Serializable {}
}
