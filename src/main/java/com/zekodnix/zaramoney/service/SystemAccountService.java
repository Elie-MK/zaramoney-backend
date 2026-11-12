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

    private final BankAccountRepository bankAccountRepository;
    private final TransactionTemplate transactionTemplate;

    public SystemAccountService(BankAccountRepository bankAccountRepository, PlatformTransactionManager transactionManager) {
        this.bankAccountRepository = bankAccountRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSystemAccounts() {
        transactionTemplate.executeWithoutResult(status -> {
            if (bankAccountRepository.findByAccountNumber(PROMO_ACCOUNT_NUMBER).isEmpty()) {
                BankAccount promo = new BankAccount()
                    .accountNumber(PROMO_ACCOUNT_NUMBER)
                    .balance(BigDecimal.ZERO.setScale(2))
                    .currency(Currency.USD)
                    .status(AccountStatus.ACTIVE)
                    .accountType(BankAccountType.SYSTEM)
                    .createdAt(Instant.now());
                bankAccountRepository.save(promo);
                LOG.info("Created system promo account {}", PROMO_ACCOUNT_NUMBER);
            }
        });
    }

    /** Must be called inside a transaction. Locks the promo account row (bonus grants are serialized). */
    public BankAccount lockPromoAccount() {
        return bankAccountRepository
            .findByAccountNumberForUpdate(PROMO_ACCOUNT_NUMBER)
            .orElseThrow(() -> new IllegalStateException("System promo account is missing"));
    }
}
