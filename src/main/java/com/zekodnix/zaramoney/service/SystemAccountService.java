package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Internal ZaraMoney accounts ({@link BankAccountType#SYSTEM}): they have no user, may go negative and are never
 * usable as a customer transfer counterparty.
 */
@Service
public class SystemAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemAccountService.class);

    /** Funds the signup bonus. Its (negative) balance is the total bonus issued. Prefix 9999 never collides with customer accounts (2512). */
    public static final String PROMO_ACCOUNT_NUMBER = "999900000001";

    /**
     * Cash / settlement account: the counterpart of admin deposits (cash received outside the app and credited to a
     * wallet). Its (negative) balance is the total cash deposited that ZaraMoney owes to customers.
     */
    public static final String SETTLEMENT_ACCOUNT_NUMBER = "999900000002";

    /**
     * Fee revenue: every transfer / withdrawal fee is credited here through the ledger. Its balance is the total fee
     * income since launch; per-period income comes from its ledger entries ({@code RevenueReportService}).
     */
    public static final String FEE_REVENUE_ACCOUNT_NUMBER = "999900000003";

    private final BankAccountRepository bankAccountRepository;
    private final TransactionTemplate transactionTemplate;

    public SystemAccountService(BankAccountRepository bankAccountRepository, PlatformTransactionManager transactionManager) {
        this.bankAccountRepository = bankAccountRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSystemAccounts() {
        transactionTemplate.executeWithoutResult(status -> {
            ensureSystemAccount(PROMO_ACCOUNT_NUMBER, "promo");
            ensureSystemAccount(SETTLEMENT_ACCOUNT_NUMBER, "settlement");
            ensureSystemAccount(FEE_REVENUE_ACCOUNT_NUMBER, "fee revenue");
        });
    }

    private void ensureSystemAccount(String accountNumber, String label) {
        if (bankAccountRepository.findByAccountNumber(accountNumber).isEmpty()) {
            BankAccount account = new BankAccount()
                .accountNumber(accountNumber)
                .balance(BigDecimal.ZERO.setScale(2))
                .currency(Currency.USD)
                .status(AccountStatus.ACTIVE)
                .accountType(BankAccountType.SYSTEM)
                .createdAt(Instant.now());
            bankAccountRepository.save(account);
            LOG.info("Created system {} account {}", label, accountNumber);
        }
    }

    /** Must be called inside a transaction. Locks the promo account row (bonus grants are serialized). */
    public BankAccount lockPromoAccount() {
        return bankAccountRepository
            .findByAccountNumberForUpdate(PROMO_ACCOUNT_NUMBER)
            .orElseThrow(() -> new IllegalStateException("System promo account is missing"));
    }

    /** Must be called inside a transaction, after the customer row is locked (lock order: 2512... before 9999...). */
    public BankAccount lockSettlementAccount() {
        return bankAccountRepository
            .findByAccountNumberForUpdate(SETTLEMENT_ACCOUNT_NUMBER)
            .orElseThrow(() -> new IllegalStateException("System settlement account is missing"));
    }

    /**
     * Must be called inside a transaction, after the customer rows are locked (system accounts are always locked last,
     * so lock order stays 2512... before 9999...). Call it as late as possible: every fee-bearing operation serializes on it.
     */
    public BankAccount lockFeeRevenueAccount() {
        return bankAccountRepository
            .findByAccountNumberForUpdate(FEE_REVENUE_ACCOUNT_NUMBER)
            .orElseThrow(() -> new IllegalStateException("System fee revenue account is missing"));
    }
}
