package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Welcome bonus: moves {@link #BONUS_AMOUNT} from the SYSTEM promo account to a new customer account, with a
 * BONUS transaction and a balanced pair of ledger entries (DEBIT promo / CREDIT customer), so no money is created
 * outside the ledger.
 */
@Service
public class SignupBonusService {

    public static final BigDecimal BONUS_AMOUNT = new BigDecimal("5.00");

    private final SystemAccountService systemAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;
    private final TransactionRecordService transactionRecordService;
    private final LedgerEntryServicePlus ledgerEntryServicePlus;
    private final TransactionValidatorService transactionValidatorService;

    public SignupBonusService(
        SystemAccountService systemAccountService,
        BankAccountRepository bankAccountRepository,
        BankAccountMapper bankAccountMapper,
        TransactionRecordService transactionRecordService,
        LedgerEntryServicePlus ledgerEntryServicePlus,
        TransactionValidatorService transactionValidatorService
    ) {
        this.systemAccountService = systemAccountService;
        this.bankAccountRepository = bankAccountRepository;
        this.bankAccountMapper = bankAccountMapper;
        this.transactionRecordService = transactionRecordService;
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
        this.transactionValidatorService = transactionValidatorService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionRecordDTO grant(String customerAccountNumber) {
        // Lock order: promo (9999...) is always locked after the customer account (2512...), same as lockAccounts' ordering
        BankAccount customer = bankAccountRepository
            .findByAccountNumberForUpdate(customerAccountNumber)
            .orElseThrow(() -> new IllegalStateException("Account to credit not found"));
        BankAccount promo = systemAccountService.lockPromoAccount();

        promo.setBalance(promo.getBalance().subtract(BONUS_AMOUNT).setScale(2, RoundingMode.HALF_UP));
        customer.setBalance(customer.getBalance().add(BONUS_AMOUNT).setScale(2, RoundingMode.HALF_UP));

        var promoDto = bankAccountMapper.toDto(promo);
        var customerDto = bankAccountMapper.toDto(customer);

        TransactionRecordDTO tx = new TransactionRecordDTO();
        tx.setTransactionReference(transactionValidatorService.generateTransactionReference());
        tx.setTransactionType(TransactionType.BONUS);
        tx.setSendAmount(BONUS_AMOUNT);
        tx.setReceiveAmount(BONUS_AMOUNT);
        tx.setCurrencySendAmount(Currency.USD);
        tx.setCurrencyReceiveAmount(Currency.USD);
        tx.setDescription("Bonus de bienvenue");
        tx.setSender(promoDto);
        tx.setReceiver(customerDto);
        tx.setExchangeRate(BigDecimal.ONE);
        tx.setFee(BigDecimal.ZERO);
        tx.setCreatedAt(Instant.now());
        tx.setTransactionDate(Instant.now());
        tx.setTransactionStatus(TransactionStatus.COMPLETED);
        tx.setFraudStatus(FraudStatus.CLEAN);
        tx.setRiskScore(0);
        TransactionRecordDTO saved = transactionRecordService.save(tx);

        var debit = ledgerEntryServicePlus.buildEntry(saved, promoDto, BONUS_AMOUNT, EntryType.DEBIT);
        var credit = ledgerEntryServicePlus.buildEntry(saved, customerDto, BONUS_AMOUNT, EntryType.CREDIT);
        ledgerEntryServicePlus.validateDoubleEntry(debit, credit);
        return saved;
    }
}
