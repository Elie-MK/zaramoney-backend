package com.zekodnix.zaramoney.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Result of {@code LedgerReconciliationService.reconcile()}. {@code ok} is false when any of the four checks found
 * an issue; the {@code legacy*} fields are information only (money that predates the ledger, see the service).
 * Lists are capped at {@code LIMIT} rows each; {@code issueCount} is the real total.
 */
public record ReconciliationReportDTO(
    Instant ranAt,
    boolean ok,
    long issueCount,
    List<UnbalancedTransaction> unbalancedTransactions,
    List<BalanceDrift> balanceDrifts,
    List<ChainBreak> chainBreaks,
    List<String> completedTransactionsWithoutLedger,
    Instant balanceTrackingSince,
    BigDecimal totalBalances,
    BigDecimal totalLedgerNet,
    BigDecimal legacyUnledgeredTotal,
    long legacyAccounts
) {
    /** Σ DEBIT != Σ CREDIT over the entries of one transaction. */
    public record UnbalancedTransaction(String transactionReference, BigDecimal debits, BigDecimal credits) {}

    /** Current balance != balance_after of the account's latest ledger entry: the balance changed outside the ledger. */
    public record BalanceDrift(String accountNumber, BigDecimal balance, BigDecimal lastBalanceAfter, Long lastEntryId) {}

    /** previous balance_after ± amount != balance_after for two consecutive entries of one account. */
    public record ChainBreak(String accountNumber, Long entryId, BigDecimal previousBalanceAfter, BigDecimal signedAmount, BigDecimal balanceAfter) {}
}
