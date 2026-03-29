package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
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

    public void checkAmountValidation(TransactionRecordVM vm, LockedAccountsDTO accounts) {
        BigDecimal amountToDebit = calculate(vm);

        if (amountToDebit == null || amountToDebit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Invalid amount", "transaction", "invalidamount");
        }

        if (accounts.getSenderAccount().getBalance().compareTo(amountToDebit) < 0) {
            throw new BadRequestAlertException("Insufficient balance", "transaction", "insufficientbalance");
        }

        if (accounts.getSenderAccount().getBalance().subtract(amountToDebit).compareTo(MIN_BALANCE) < 0) {
            throw new BadRequestAlertException(
                "Transaction denied: account must maintain a minimum balance of " + MIN_BALANCE,
                "transaction",
                "minimumbalance"
            );
        }
    }

    public TransactionRecordDTO process(TransactionRecordVM vm, LockedAccountsDTO accounts) {
        var sender = accounts.getSenderAccount();
        var receiver = accounts.getReceiverAccount();

        // 1. Calculate amount
        BigDecimal receiveAmount = calculate(vm);

        sender.setBalance(sender.getBalance().subtract(receiveAmount).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(receiveAmount).setScale(2, RoundingMode.HALF_UP));

        BankAccountDTO senderDto = bankAccountMapper.toDto(sender);
        BankAccountDTO receiverDto = bankAccountMapper.toDto(receiver);

        // Save updated accounts
        bankAccountService.save(senderDto);
        bankAccountService.save(receiverDto);

        var transaction = buildTransaction(vm, receiveAmount, sender, receiver);
        return transactionRecordService.save(transaction);
    }

    private TransactionRecordDTO buildTransaction(
        TransactionRecordVM transactionRecordVM,
        BigDecimal receiveAmount,
        BankAccount currentUser,
        BankAccount receiverBankAccount
    ) {
        TransactionRecordDTO transaction = new TransactionRecordDTO();
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(transactionRecordVM.getTransactionType());
        transaction.setSendAmount(transactionRecordVM.getSendAmount());
        transaction.setReceiveAmount(receiveAmount);
        transaction.setCurrencySendAmount(transactionRecordVM.getCurrencySendAmount());
        transaction.setCurrencyReceiveAmount(receiverBankAccount.getCurrency());
        transaction.setDescription(transactionRecordVM.getDescription());
        transaction.setSender(bankAccountMapper.toDto(currentUser));
        transaction.setReceiver(bankAccountMapper.toDto(receiverBankAccount));

        // default risk score
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDate(Instant.now());
        transaction.setFraudStatus(FraudStatus.CLEAN);
        transaction.setRiskScore(0);

        return transaction;
    }

    private BigDecimal calculate(TransactionRecordVM vm) {
        if (vm.getSendAmount() == null || vm.getSendAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Send amount must be greater than zero");
        }

        Currency from = vm.getCurrencySendAmount();
        Currency to = vm.getCurrencyReceiveAmount();

        if (from == null || to == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }

        if (from == Currency.TND && to == Currency.TND) {
            throw new IllegalArgumentException("Operation not supported: TND -> TND");
        }

        // Same currency → no conversion
        if (from == to) {
            return vm.getSendAmount().setScale(SCALE, RoundingMode.HALF_UP);
        }

        // TND → USD
        if (from == Currency.TND && to == Currency.USD) {
            return vm.getSendAmount().divide(TND_TO_USD_RATE, SCALE, RoundingMode.HALF_UP);
        }

        throw new UnsupportedOperationException("Unsupported currency conversion: " + from + " -> " + to);
    }

    private String generateTransactionReference() {
        return "ZMCT-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
