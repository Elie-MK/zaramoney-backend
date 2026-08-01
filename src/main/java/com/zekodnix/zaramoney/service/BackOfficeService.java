package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zekodnix.zaramoney.domain.Authority;
import com.zekodnix.zaramoney.domain.enumeration.*;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.dto.DepositDto;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.Set;

@Service
public class BackOfficeService {

    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeService.class);

    private final BankAccountService bankAccountService;
    private final UserServicePlus userServicePlus;
    private final UserService userService;
    private final TransactionRecordService transactionRecordService;
    private final TransactionRecordServicePlus  transactionRecordServicePlus;
    private final TransactionValidatorService transactionValidatorService;
    private final BankAccountMapper bankAccountMapper;
    private final LedgerEntryServicePlus ledgerEntryServicePlus;
    private final NotificationServicePlus notificationServicePlus;

    public BackOfficeService(BankAccountService bankAccountService, UserService userService, UserAccountService userAccountService, UserServicePlus userServicePlus, UserService userService1, TransactionRecordService transactionRecordService, TransactionRecordServicePlus transactionRecordServicePlus, TransactionValidatorService transactionValidatorService, BankAccountMapper bankAccountMapper, LedgerEntryServicePlus ledgerEntryServicePlus, NotificationServicePlus notificationServicePlus) {
        this.bankAccountService = bankAccountService;
        this.userServicePlus = userServicePlus;
        this.userService = userService1;
        this.transactionRecordService = transactionRecordService;
        this.transactionRecordServicePlus = transactionRecordServicePlus;
        this.transactionValidatorService = transactionValidatorService;
        this.bankAccountMapper = bankAccountMapper;
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
        this.notificationServicePlus = notificationServicePlus;
    }

    @Transactional
    public TransactionRecordDTO addMoneyToUserAccount(DepositDto depositDto) {
        var userAccount = bankAccountService.findByAccountNumber(depositDto.getUserAccountNumber()).orElseThrow();
        var user = userServicePlus.findOneByLogin(depositDto.getUserEmail());
        var currentUser = userService.getUserWithAuthorities().orElseThrow();


        if (currentUser.getAuthorities().stream()
            .noneMatch(auth -> auth.getName().equals(AuthoritiesConstants.ADMIN))) {
            throw new AuthorizationDeniedException("You are not authorized to perform this operation");
        }

       if (!userAccount.getUser().getLogin().equals(user.getLogin())) {
           throw new AuthorizationDeniedException("The account number and the user account number do not match");
       }

       userAccount.setBalance(userAccount.getBalance().add(depositDto.getAmount()));

       bankAccountService.save(userAccount);

        TransactionRecordDTO transaction = new TransactionRecordDTO();
        transaction.setTransactionReference(transactionValidatorService.generateTransactionReference());
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setSendAmount(depositDto.getAmount());
        transaction.setReceiveAmount(depositDto.getAmount());
        transaction.setCurrencySendAmount(Currency.USD);
        transaction.setCurrencyReceiveAmount(Currency.USD);
        transaction.setDescription("Depot de " + depositDto.getAmount() + " USD sur mon compte");
        transaction.setSender(null);
        transaction.setReceiver(userAccount);
        transaction.setExchangeRate(BigDecimal.valueOf(2.81));
        transaction.setCreatedAt(Instant.now());

        // default risk score
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDate(Instant.now());
        transaction.setFraudStatus(FraudStatus.CLEAN);
        transaction.setRiskScore(0);

        var savedTransactionRecord = transactionRecordService.save(transaction);
        ledgerEntryServicePlus.buildEntry(savedTransactionRecord, userAccount, depositDto.getAmount(), EntryType.CREDIT);
        notificationServicePlus.createTransactionNotification(savedTransactionRecord, user, depositDto.getAmount(), false);

        return savedTransactionRecord;
    }
}
