package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.TransactionDetails;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionRecordServicePlus {

    private static final String INSUFFICIENT_BALANCE_MESSAGE = "Insufficient balance";
    private static final BigDecimal USD_TO_TND_RATE = BigDecimal.valueOf(2.81);
    private static final BigDecimal TND_TO_USD_RATE = BigDecimal.valueOf(2.85);
    private static final int SCALE = 2;
    private final TransactionRecordService transactionRecordService;
    BigDecimal MIN_BALANCE = BigDecimal.valueOf(5);

    private final TransactionRecordRepository transactionRecordRepository;
    private final TransactionRecordMapper transactionRecordMapper;
    private final UserService userService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankAccountService bankAccountService;

    public TransactionRecordServicePlus(
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper,
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        IdempotencyRecordRepository idempotencyRecordRepository,
        PasswordEncoder passwordEncoder,
        TransactionRecordService transactionRecordService,
        BankAccountService bankAccountService
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordMapper = transactionRecordMapper;
        this.userService = userService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionRecordService = transactionRecordService;
        this.bankAccountService = bankAccountService;
    }

    @Transactional
    public TransactionRecordDTO createTransactionRecord(TransactionRecordVM transactionRecordVM)
        throws AccessDeniedException, JsonProcessingException {
        // Authenticated user (trust Spring Security context only)
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));

        if (
            transactionRecordVM.getPassword() == null ||
            !passwordEncoder.matches(transactionRecordVM.getPassword(), currentUser.getPassword())
        ) {
            throw new BadRequestAlertException("Invalid transaction password", "transaction", "invalidpassword");
        }

        // Check user details account;

        var findCurrentUserBankAccount = bankAccountService.findOne(currentUser.getId());

        // Check receiver account

        // Validate amount
        //        if (transactionRecordVM.getSendAmount() == null || transactionRecordVM.getSendAmount().compareTo(BigDecimal.ZERO) <= 0) {
        //            throw new IllegalArgumentException("Invalid amount");
        //        }
        //
        //        // Check sufficient balance
        //        if (currentUserDetailsAccount.getAccountBalance().compareTo(transactionRecordVM.getSendAmount()) < 0) {
        //            throw new BadRequestAlertException(INSUFFICIENT_BALANCE_MESSAGE, "transaction", "insufficientbalance");
        //        }
        //
        //        if (currentUserDetailsAccount.getAccountBalance().subtract(transactionRecordVM.getSendAmount()).compareTo(MIN_BALANCE) < 0) {
        //            throw new BadRequestAlertException(
        //                "Transaction denied: account must maintain a minimum balance of 5",
        //                "transaction",
        //                "minimumbalance"
        //            );
        //        }
        //
        //        // Idempotency check (HASHED key)
        //        String keyHash = hashIdempotencyKey(transactionRecordVM.getIdempotencyKey());
        //
        //        // check idempotency using idempotency key hash
        //        var existing = idempotencyRecordRepository.findByKeyHash(keyHash);
        //        if (existing.isPresent()) {
        //            return transactionRecordMapper.fromJson(existing.get().getResponseBody());
        //        }
        //
        //        IdempotencyRecord idempotency = reserveIdempotency(transactionRecordVM, keyHash, currentUser);
        //
        //        // Calculate receive amount
        //        BigDecimal receiveAmount = calculateReceiveAmount(transactionRecordVM);
        //
        //        // Create transaction record
        //        TransactionRecord transaction = getTransactionRecord(
        //            transactionRecordVM,
        //            receiveAmount,
        //            currentUserDetailsAccount,
        //            receiverDetailsAccount,
        //            currentUser
        //        );
        //
        //        // Balance updates
        //        currentUserDetailsAccount.setAccountBalance(
        //            currentUserDetailsAccount.getAccountBalance().subtract(transactionRecordVM.getSendAmount())
        //        );
        //        receiverDetailsAccount.setAccountBalance(receiverDetailsAccount.getAccountBalance().add(transactionRecordVM.getSendAmount()));
        //
        //        // Save updated accounts
        //        userDetailsAccountService.save(currentUserDetailsAccount);
        //        userDetailsAccountService.save(receiverDetailsAccount);
        //
        //        // Save idempotency record
        //        completeIdempotency(idempotency, transaction);
        //
        //        return transactionRecordMapper.toDto(transaction);
        return null;
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

    private IdempotencyRecord reserveIdempotency(TransactionRecordVM vm, String keyHash, User currentUser) {
        IdempotencyRecord idempotency = new IdempotencyRecord();
        idempotency.setKeyHash(keyHash);
        idempotency.setEndpoint(vm.getEndpoint());
        idempotency.setUserId(currentUser.getId());
        idempotency.setResponseStatus(0); // pending

        return idempotencyRecordRepository.save(idempotency);
    }

    private void completeIdempotency(IdempotencyRecord idempotency, TransactionRecord transaction) throws JsonProcessingException {
        idempotency.setTransactionReference(transaction.getTransactionReference());
        idempotency.setResponseStatus(1); // success

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String responseJson = mapper.writeValueAsString(transactionRecordMapper.toDto(transaction));

        idempotency.setResponseBody(responseJson);
        idempotency.responseStatus(1);

        idempotencyRecordRepository.save(idempotency);
    }

    //    private TransactionRecord getTransactionRecord(
    //        TransactionRecordVM transactionRecordVM,
    //        BigDecimal receiveAmount,
    //        UserDetailsAccountDTO currentUserDetailsAccount,
    //        UserDetailsAccountDTO receiverDetailsAccount,
    //        User currentUser
    //    ) {
    //        // Create transaction
    //        TransactionRecord transaction = new TransactionRecord();
    //        transaction.setTransactionReference(generateTransactionReference());
    //        transaction.setTransactionType(transactionRecordVM.getTransactionType());
    //        transaction.setSendAmount(transactionRecordVM.getSendAmount());
    //        transaction.setReceiveAmount(receiveAmount);
    //        transaction.setCurrencySendAmount(transactionRecordVM.getCurrencySendAmount());
    //        transaction.setCurrencyReceiveAmount(transactionRecordVM.getCurrencyReceiveAmount());
    //        transaction.setSenderAccountNumber(currentUserDetailsAccount.getAccountNumber());
    //        transaction.setReceiverAccountNumber(receiverDetailsAccount.getAccountNumber());
    //        transaction.setDescription(transactionRecordVM.getDescription());
    //        transaction.setUserLogin(currentUser);
    //        // default risk score
    //        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
    //        transaction.setTransactionDate(Instant.now());
    //        transaction.setFraudStatus(FraudStatus.CLEAN);
    //        transaction.setRiskScore(0);
    //
    //        transactionRecordRepository.save(transaction);
    //        return transaction;
    //    }

    //    public Page<TransactionRecordDTO> findAllForCurrentUser(Pageable pageable)  {
    //        return transactionRecordService.findAllForCurrentUser(pageable);
    //    }

    private String hashIdempotencyKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String generateTransactionReference() {
        return "ZMCT-" + Instant.now().toEpochMilli();
    }

    private BigDecimal calculateReceiveAmount(TransactionRecordVM vm) {
        if (vm.getSendAmount() == null || vm.getSendAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Send amount must be greater than zero");
        }

        Currency from = vm.getCurrencySendAmount();
        Currency to = vm.getCurrencyReceiveAmount();

        if (from == null || to == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }

        // Same currency → no conversion
        if (from == to) {
            return vm.getSendAmount().setScale(SCALE, RoundingMode.HALF_UP);
        }

        // USD → TND
        if (from == Currency.USD && to == Currency.TND) {
            return vm.getSendAmount().multiply(USD_TO_TND_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        }

        // TND → USD
        if (from == Currency.TND && to == Currency.USD) {
            return vm.getSendAmount().multiply(TND_TO_USD_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        }

        throw new UnsupportedOperationException("Unsupported currency conversion: " + from + " -> " + to);
    }
}
