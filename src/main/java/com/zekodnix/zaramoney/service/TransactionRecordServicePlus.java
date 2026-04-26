package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zekodnix.zaramoney.service.criteria.TransactionRecordCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import java.nio.file.AccessDeniedException;
import java.util.*;
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

    public TransactionRecordServicePlus(
        UserService userService,
        BankAccountService bankAccountService,
        IdempotencyRecordServicePlus idempotencyRecordServicePlus,
        BankAccountServicePlus bankAccountServicePlus,
        TransactionValidatorService transactionValidatorService,
        TransactionRecordService transactionRecordService
    ) {
        this.userService = userService;
        this.bankAccountService = bankAccountService;
        this.idempotencyRecordServicePlus = idempotencyRecordServicePlus;
        this.bankAccountServicePlus = bankAccountServicePlus;
        this.transactionValidatorService = transactionValidatorService;
        this.transactionRecordService = transactionRecordService;
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

        // Process transaction (core logic)
        var result = transactionValidatorService.process(vm, accounts);

        // Complete idempotency
        idempotencyRecordServicePlus.completeIdempotency(reservation, result);

        return result;
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
