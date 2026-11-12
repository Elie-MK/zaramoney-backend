package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.fraud.FraudAssessment;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/** Rules and balance movement for customer transfers / withdrawals. Always called on rows locked by the caller. */
@Service
public class TransactionValidatorService {

    private final BankAccountMapper bankAccountMapper;
    private final TransactionRecordService transactionRecordService;
    private final FeeSchedule feeSchedule;

    public TransactionValidatorService(
        BankAccountMapper bankAccountMapper,
        TransactionRecordService transactionRecordService,
        FeeSchedule feeSchedule
    ) {
        this.bankAccountMapper = bankAccountMapper;
        this.transactionRecordService = transactionRecordService;
        this.feeSchedule = feeSchedule;
    }

    /**
     * Both accounts must be ACTIVE and in the same currency, and the requested currencies (if the app sent them) must
     * match: there is no currency conversion.
     */
    public void checkAccounts(LockedAccountsDTO accounts, Currency requestedSendCurrency, Currency requestedReceiveCurrency) {
        BankAccount sender = accounts.getSenderAccount();
        BankAccount receiver = accounts.getReceiverAccount();
        if (sender.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("Your account is " + sender.getStatus() + ", contact support", "accountnotactive");
        }
        if (receiver.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.badRequest("The receiving account cannot receive money", "receivernotactive");
        }
        Currency currency = sender.getCurrency();
        if (
            receiver.getCurrency() != currency ||
            (requestedSendCurrency != null && requestedSendCurrency != currency) ||
            (requestedReceiveCurrency != null && requestedReceiveCurrency != currency)
        ) {
            throw ApiException.badRequest("Currency conversion is not supported: both sides must be in " + currency, "currencymismatch");
        }
    }

    /**
     * The sender is debited {@code amount + fee}: both must be covered while keeping the configured minimum balance.
     */
    public void checkAmountValidation(BigDecimal amount, BigDecimal fee, LockedAccountsDTO accounts) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Invalid amount", "invalidamount");
        }
        BigDecimal totalDebit = amount.add(fee == null ? BigDecimal.ZERO : fee);
        BigDecimal balance = accounts.getSenderAccount().getBalance();

        if (balance.compareTo(totalDebit) < 0) {
            throw ApiException.badRequest(
                "Insufficient balance: " + totalDebit + " needed (amount " + amount + " + fee " + fee + ")",
                "insufficientbalance"
            );
        }
        BigDecimal minBalance = feeSchedule.minBalance();
        if (balance.subtract(totalDebit).compareTo(minBalance) < 0) {
            throw ApiException.badRequest(
                "Transaction denied: account must maintain a minimum balance of " + minBalance + " after amount and fee",
                "minimumbalance"
            );
        }
    }

    /**
     * Moves the money on the locked (managed) rows — sender {@code -(amount + fee)}, receiver {@code +amount} — and saves
     * the COMPLETED transaction. The fee itself is credited to the fee revenue account by the ledger step.
     */
    public TransactionRecordDTO move(
        TransactionType type,
        LockedAccountsDTO accounts,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        FraudAssessment fraud
    ) {
        BankAccount sender = accounts.getSenderAccount();
        BankAccount receiver = accounts.getReceiverAccount();

        sender.setBalance(sender.getBalance().subtract(amount.add(fee)).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(amount).setScale(2, RoundingMode.HALF_UP));

        TransactionRecordDTO transaction = record(type, accounts, amount, fee, description, fraud, TransactionStatus.COMPLETED);
        return transactionRecordService.save(transaction);
    }

    /**
     * A payment rejected by the fraud rules (ENFORCE mode): FAILED, no balance change, no ledger. Not saved here: the
     * money transaction rolls back first, then the caller saves it in a new transaction for the review queue.
     */
    public TransactionRecordDTO blockedAttempt(
        TransactionType type,
        LockedAccountsDTO accounts,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        FraudAssessment fraud
    ) {
        return record(type, accounts, amount, fee, description, fraud, TransactionStatus.FAILED);
    }

    private TransactionRecordDTO record(
        TransactionType type,
        LockedAccountsDTO accounts,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        FraudAssessment fraud,
        TransactionStatus status
    ) {
        BankAccount sender = accounts.getSenderAccount();
        BankAccount receiver = accounts.getReceiverAccount();
        Instant now = Instant.now();
        TransactionRecordDTO transaction = new TransactionRecordDTO();
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(type);
        transaction.setSendAmount(amount);
        transaction.setReceiveAmount(amount);
        transaction.setFee(fee);
        transaction.setExchangeRate(BigDecimal.ONE);
        transaction.setCurrencySendAmount(sender.getCurrency());
        transaction.setCurrencyReceiveAmount(receiver.getCurrency());
        transaction.setDescription(description);
        transaction.setSender(bankAccountMapper.toDto(sender));
        transaction.setReceiver(bankAccountMapper.toDto(receiver));
        transaction.setCreatedAt(now);
        transaction.setTransactionDate(now);
        transaction.setTransactionStatus(status);
        transaction.setFraudStatus(fraud.status());
        transaction.setRiskScore(fraud.score());
        transaction.setFraudReasons(fraud.reasonsText());
        return transaction;
    }

    public String generateTransactionReference() {
        return "ZMCT-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
