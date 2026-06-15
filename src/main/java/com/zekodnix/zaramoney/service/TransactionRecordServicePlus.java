package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import com.zekodnix.zaramoney.web.rest.vm.WithdrawalTransactionRecordVM;
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
    private final UserDetailsAccountService userDetailsAccountService;
    private final UserServicePlus userServicePlus;

    public TransactionRecordServicePlus(
        UserService userService,
        BankAccountService bankAccountService,
        IdempotencyRecordServicePlus idempotencyRecordServicePlus,
        BankAccountServicePlus bankAccountServicePlus,
        TransactionValidatorService transactionValidatorService,
        TransactionRecordService transactionRecordService,
        LedgerEntryServicePlus ledgerEntryServicePlus,
        TransactionFeeServicePlus transactionFeeServicePlus,
        NotificationServicePlus notificationServicePlus,
        UserDetailsAccountService userDetailsAccountService,
        UserServicePlus userServicePlus
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
        this.userDetailsAccountService = userDetailsAccountService;
        this.userServicePlus = userServicePlus;
    }

    @Transactional
    public TransactionRecordDTO createTransactionRecord(TransactionRecordVM vm) throws AccessDeniedException, JsonProcessingException {
        // 1. Idempotency check
        var existing = idempotencyRecordServicePlus.check(vm.getIdempotencyKey());
        if (existing != null) return existing;

        // Get sender account
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));

        var sender = bankAccountService.findByUserIsCurrentUser().stream().findFirst().orElseThrow();

        var receiver = bankAccountService.findByAccountNumber(vm.getReceiverAccountNumber()).orElseThrow();
        var currentReceiver = userServicePlus.findOneByLogin(receiver.getUser().getLogin());

        // Security check
        userService.validateTransactionPassword(vm.getPassword(), currentUser);

        // Lock accounts (critical section)
        var accounts = bankAccountServicePlus.lockAccounts(sender.getAccountNumber(), vm.getReceiverAccountNumber());

        // Ownership check
        if (!sender.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not allowed to use this account");
        }

        // Validate business rules
        transactionValidatorService.checkAmountValidation(vm.getSendAmount(), accounts);

        // Reserve idempotency
        var reservation = idempotencyRecordServicePlus.reserveIdempotency(vm.getIdempotencyKey(), currentUser);

        var amount = vm.getSendAmount();
        var fee = calculateTransactionFee(amount);

        // Process transaction (core logic)
        var result = transactionValidatorService.process(vm, accounts, fee);

        transactionFeeServicePlus.createTransactionFee(fee, result);

        var debit = ledgerEntryServicePlus.buildEntry(result, sender, vm.getSendAmount(), EntryType.DEBIT);
        var credit = ledgerEntryServicePlus.buildEntry(result, result.getReceiver(), vm.getSendAmount(), EntryType.CREDIT);

        ledgerEntryServicePlus.validateDoubleEntry(debit, credit);

        // Complete idempotency
        idempotencyRecordServicePlus.completeIdempotency(reservation, result);

        notificationServicePlus.createTransactionNotification(result, currentUser, result.getSendAmount(), true);
        notificationServicePlus.createTransactionNotification(result, currentReceiver, result.getReceiveAmount(), false);

        return result;
    }

    @Transactional
    public TransactionRecordDTO withdrawalTransaction(WithdrawalTransactionRecordVM vm)
        throws AccessDeniedException, JsonProcessingException {
        // 1. Idempotency check
        var existing = idempotencyRecordServicePlus.check(vm.getIdempotencyKey());
        if (existing != null) return existing;

        // 2. Connected User
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));
        var sender = bankAccountService.findByUserIsCurrentUser().stream().findFirst().orElseThrow();

        // Ownership check
        if (!sender.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not allowed to use this account");
        }

        userService.validateTransactionPassword(vm.getPassword(), currentUser);

        var agentAccount = bankAccountService.findByAccountNumber(vm.getAgentAccountNumber()).orElseThrow();
        var agentDetails = userDetailsAccountService.findByUserEmail(agentAccount.getUser().getLogin()).orElseThrow();
        var currentAgentReceiver = userServicePlus.findOneByLogin(agentAccount.getUser().getLogin());

        if (sender.getAccountNumber().equals(vm.getAgentAccountNumber())) {
            throw new AccessDeniedException("Operation not permitted: sender and receiver accounts must be different.");
        }

        if (!agentDetails.getIsAgent()) {
            throw new AccessDeniedException("The account is not an agent account");
        }

        // Lock accounts (critical section)
        var accounts = bankAccountServicePlus.lockAccounts(sender.getAccountNumber(), agentAccount.getAccountNumber());

        // Validate business rules
        transactionValidatorService.checkAmountValidation(vm.getSendAmount(), accounts);

        // Reserve idempotency
        var reservation = idempotencyRecordServicePlus.reserveIdempotency(vm.getIdempotencyKey(), currentUser);

        var amount = vm.getSendAmount();
        var fee = calculateWithdrawalFee(amount);

        // Process transaction (core logic)
        var result = transactionValidatorService.processWithdrawal(vm, accounts, fee);

        transactionFeeServicePlus.createTransactionFee(fee, result);

        var debit = ledgerEntryServicePlus.buildEntry(result, sender, vm.getSendAmount(), EntryType.DEBIT);
        var credit = ledgerEntryServicePlus.buildEntry(result, result.getReceiver(), vm.getSendAmount(), EntryType.CREDIT);

        ledgerEntryServicePlus.validateDoubleEntry(debit, credit);

        // Complete idempotency
        idempotencyRecordServicePlus.completeIdempotency(reservation, result);

        notificationServicePlus.createTransactionNotification(result, currentUser, result.getSendAmount(), true);
        notificationServicePlus.createTransactionNotification(result, currentAgentReceiver, result.getReceiveAmount(), false);

        return result;
    }

    private BigDecimal calculateWithdrawalFee(BigDecimal amount) {
        BigDecimal min = BigDecimal.valueOf(1);
        BigDecimal tier1Max = BigDecimal.valueOf(5);
        BigDecimal tier2Max = BigDecimal.valueOf(20);
        BigDecimal tier3Max = BigDecimal.valueOf(50);

        if (amount.compareTo(min) < 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        if (amount.compareTo(tier1Max) <= 0) {
            return applyRate(amount, 0.0130);
        }
        if (amount.compareTo(tier2Max) <= 0) {
            return applyRate(amount, 0.08);
        }
        if (amount.compareTo(tier3Max) <= 0) {
            return applyRate(amount, 0.0250);
        }

        return applyRate(amount, 0.0199);
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
        return amount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
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
