package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import com.zekodnix.zaramoney.web.rest.vm.WithdrawalTransactionRecordVM;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class TransactionValidatorService {

    BigDecimal MIN_BALANCE = BigDecimal.valueOf(5);
    private static final BigDecimal USD_TO_TND_RATE = BigDecimal.valueOf(2.81);
    private static final BigDecimal TND_TO_USD_RATE = BigDecimal.valueOf(2.85);
    private static final int SCALE = 2;

    private final BankAccountMapper bankAccountMapper;
    private final BankAccountService bankAccountService;
    private final TransactionRecordService transactionRecordService;

    public TransactionValidatorService(
        BankAccountMapper bankAccountMapper,
        BankAccountService bankAccountService,
        TransactionRecordService transactionRecordService
    ) {
        this.bankAccountMapper = bankAccountMapper;
        this.bankAccountService = bankAccountService;
        this.transactionRecordService = transactionRecordService;
    }

    public void checkAmountValidation(BigDecimal amount, LockedAccountsDTO accounts) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Invalid amount", "transaction", "invalidamount");
        }

        if (accounts.getSenderAccount().getBalance().compareTo(amount) < 0) {
            throw new BadRequestAlertException("Insufficient balance", "transaction", "insufficientbalance");
        }

        if (accounts.getSenderAccount().getBalance().subtract(amount).compareTo(MIN_BALANCE) < 0) {
            throw new BadRequestAlertException(
                "Transaction denied: account must maintain a minimum balance of " + MIN_BALANCE,
                "transaction",
                "minimumbalance"
            );
        }
    }

    public TransactionRecordDTO process(TransactionRecordVM vm, LockedAccountsDTO accounts, BigDecimal fee) {
        var sender = accounts.getSenderAccount();
        var receiver = accounts.getReceiverAccount();

        // 1. Calculate amount
        BigDecimal totalDebit = vm.getSendAmount().add(fee);

        sender.setBalance(sender.getBalance().subtract(totalDebit).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(vm.getSendAmount()).setScale(2, RoundingMode.HALF_UP));

        BankAccountDTO senderDto = bankAccountMapper.toDto(sender);
        BankAccountDTO receiverDto = bankAccountMapper.toDto(receiver);

        // Save updated accounts
        bankAccountService.save(senderDto);
        bankAccountService.save(receiverDto);

        var transaction = buildTransaction(vm, sender, receiver, fee);
        return transactionRecordService.save(transaction);
    }

    public TransactionRecordDTO processWithdrawal(WithdrawalTransactionRecordVM vm, LockedAccountsDTO accounts, BigDecimal fee) {
        var sender = accounts.getSenderAccount();
        var receiver = accounts.getReceiverAccount();

        // 1. Calculate amount
        BigDecimal totalDebit = vm.getSendAmount().add(fee);

        sender.setBalance(sender.getBalance().subtract(totalDebit).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(vm.getSendAmount()).setScale(2, RoundingMode.HALF_UP));

        BankAccountDTO senderDto = bankAccountMapper.toDto(sender);
        BankAccountDTO receiverDto = bankAccountMapper.toDto(receiver);

        // Save updated accounts
        bankAccountService.save(senderDto);
        bankAccountService.save(receiverDto);

        TransactionRecordDTO transaction = new TransactionRecordDTO();
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setSendAmount(vm.getSendAmount());
        transaction.setReceiveAmount(vm.getSendAmount());
        transaction.setCurrencySendAmount(Currency.USD);
        transaction.setCurrencyReceiveAmount(Currency.USD);
        transaction.setDescription("Retrait de " + vm.getSendAmount() + " USD");
        transaction.setSender(bankAccountMapper.toDto(sender));
        transaction.setReceiver(bankAccountMapper.toDto(receiver));
        transaction.setExchangeRate(fee);
        transaction.setCreatedAt(Instant.now());

        // default risk score
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDate(Instant.now());
        transaction.setFraudStatus(FraudStatus.CLEAN);
        transaction.setRiskScore(0);
        return transactionRecordService.save(transaction);
    }

    private TransactionRecordDTO buildTransaction(
        TransactionRecordVM transactionRecordVM,
        BankAccount currentUser,
        BankAccount receiverBankAccount,
        BigDecimal fee
    ) {
        TransactionRecordDTO transaction = new TransactionRecordDTO();
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(transactionRecordVM.getTransactionType());
        transaction.setSendAmount(transactionRecordVM.getSendAmount());
        transaction.setReceiveAmount(transactionRecordVM.getSendAmount());
        transaction.setCurrencySendAmount(transactionRecordVM.getCurrencySendAmount());
        transaction.setCurrencyReceiveAmount(receiverBankAccount.getCurrency());
        transaction.setDescription(transactionRecordVM.getDescription());
        transaction.setSender(bankAccountMapper.toDto(currentUser));
        transaction.setReceiver(bankAccountMapper.toDto(receiverBankAccount));
        transaction.setExchangeRate(fee);
        transaction.setCreatedAt(Instant.now());

        // default risk score
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDate(Instant.now());
        transaction.setFraudStatus(FraudStatus.CLEAN);
        transaction.setRiskScore(0);

        return transaction;
    }

    private String generateTransactionReference() {
        return "ZMCT-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
