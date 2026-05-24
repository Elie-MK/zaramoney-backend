package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.AccessDeniedException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionRecordServicePlus {

    private final UserService userService;
    private final BankAccountService bankAccountService;
    private final IdempotencyRecordServicePlus idempotencyRecordServicePlus;
    private final BankAccountServicePlus bankAccountServicePlus;
    private final TransactionValidatorService transactionValidatorService;
    private final TransactionRecordService transactionRecordService;
    private final LedgerEntryServicePlus ledgerEntryServicePlus;
    private final TransactionFeeServicePlus transactionFeeServicePlus;
    private final NotificationServicePlus notificationServicePlus;

    public TransactionRecordServicePlus(
        UserService userService,
        BankAccountService bankAccountService,
        IdempotencyRecordServicePlus idempotencyRecordServicePlus,
        BankAccountServicePlus bankAccountServicePlus,
        TransactionValidatorService transactionValidatorService,
        TransactionRecordService transactionRecordService, LedgerEntryServicePlus ledgerEntryServicePlus, TransactionFeeServicePlus transactionFeeServicePlus, NotificationServicePlus notificationServicePlus
    ) {
        this.userService = userService;
        this.bankAccountService = bankAccountService;
        this.idempotencyRecordServicePlus = idempotencyRecordServicePlus;
        this.bankAccountServicePlus = bankAccountServicePlus;
        this.transactionValidatorService = transactionValidatorService;
        this.transactionRecordService = transactionRecordService;
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
        this.transactionFeeServicePlus = transactionFeeServicePlus;
        this.notificationServicePlus = notificationServicePlus;
    }

    @Transactional
    public TransactionRecordDTO createTransactionRecord(TransactionRecordVM vm) throws AccessDeniedException, JsonProcessingException {
        // 1. Idempotency check
        var existing = idempotencyRecordServicePlus.check(vm.getIdempotencyKey());
        if (existing != null) return existing;

        // Get sender account
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));

        var sender = bankAccountService.findByUserIsCurrentUser().stream().findFirst().orElseThrow();

        // Security check
        userService.validateTransactionPassword(vm, currentUser);

        // Lock accounts (critical section)
        var accounts = bankAccountServicePlus.lockAccounts(sender.getAccountNumber(), vm.getReceiverAccountNumber());

        // Ownership check
        if (!sender.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not allowed to use this account");
        }

        // Validate business rules
        transactionValidatorService.checkAmountValidation(vm, accounts);

        // Reserve idempotency
        var reservation = idempotencyRecordServicePlus.reserveIdempotency(vm.getIdempotencyKey(), currentUser);

        var amount = vm.getSendAmount();
        var fee = calculateTransactionFee(amount);

        // Process transaction (core logic)
        var result = transactionValidatorService.process(vm, accounts, fee);

        transactionFeeServicePlus.createTransactionFee(vm, fee, result);

        var debit = ledgerEntryServicePlus.buildEntry(result, sender, vm, EntryType.DEBIT);
        var credit = ledgerEntryServicePlus.buildEntry(result, result.getReceiver(), vm, EntryType.CREDIT);

        ledgerEntryServicePlus.validateDoubleEntry(debit, credit);

        // Complete idempotency
        idempotencyRecordServicePlus.completeIdempotency(reservation, result);

//        notificationServicePlus.createNotification(result, currentUser,result);

        return result;
    }

    private BigDecimal calculateTransactionFee(BigDecimal amount) {

        BigDecimal min = BigDecimal.valueOf(101);
        BigDecimal tier1Max = BigDecimal.valueOf(2500);

        if (amount.compareTo(min) < 0) {
            return BigDecimal.ZERO;
        }

        // 101 → 2500 => 0.95%
        if (amount.compareTo(tier1Max) <= 0) {
            return applyRate(amount, 0.0095);
        }

        // 2501+ => 0.75%
        return applyRate(amount, 0.0075);
    }

    private BigDecimal applyRate(BigDecimal amount, double rate) {
        return amount
            .multiply(BigDecimal.valueOf(rate))
            .setScale(2, RoundingMode.HALF_UP);
    }

    public Page<TransactionRecordDTO> getAllTransactionRecords(Pageable pageable) {
        return transactionRecordService.findAllWithEagerRelationships(pageable);
    }
    //    public TransactionDetails checkAccountNumber(String accountNumber, BigDecimal sendAmount) throws AccessDeniedException {
    //        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));
    //
    //        // Check user details account
    //        var currentUserDetailsAccount = userDetailsAccountService
    //            .findByUserLoginId(currentUser.getId())
    //            .orElseThrow(() -> new BadRequestAlertException("Sender account not found", "transaction", "sendernotfound"));
    //
    //        var input = accountNumber.trim();
    //
    //        if (currentUserDetailsAccount.getAccountNumber().equals(input)) {
    //            throw new BadRequestAlertException("Cannot send money to yourself", "transaction", "sendtomyself");
    //        }
    //
    //        var receiverDetailsAccount = userDetailsAccountService.findByAccountNumber(input);
    //        if (receiverDetailsAccount.isEmpty()) {
    //            throw new BadRequestAlertException("Receiver account not found", "transaction", "receivernotfound");
    //        }
    //        var receiverUser = userService.findOneByLogin(receiverDetailsAccount.get().getUserLogin().getLogin());
    //        var name = receiverUser.getFirstName() + " " + receiverUser.getLastName();
    //
    //        var response = new TransactionDetails();
    //        response.setReceiverName(name);
    //        response.setReceiverAccountNumber(input);
    //        response.setSendAmount(sendAmount);
    //        var calculateTND = sendAmount.multiply(TND_TO_USD_RATE);
    //        response.setReceiveAmount(calculateTND.setScale(SCALE, RoundingMode.HALF_UP));
    //        return response;
    //    }

}
