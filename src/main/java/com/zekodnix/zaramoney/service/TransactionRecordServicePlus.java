package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionRecordServicePlus {

    private static final String INSUFFICIENT_BALANCE_MESSAGE = "Insufficient balance";
    private static final BigDecimal USD_TO_TND_RATE = BigDecimal.valueOf(2.81);
    private static final BigDecimal TND_TO_USD_RATE = BigDecimal.valueOf(2.85);
    private static final int SCALE = 2;

    private final TransactionRecordRepository transactionRecordRepository;
    private final TransactionRecordMapper transactionRecordMapper;
    private final UserService userService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public TransactionRecordServicePlus(
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper,
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordMapper = transactionRecordMapper;
        this.userService = userService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public TransactionRecordDTO createTransactionRecord(TransactionRecordVM transactionRecordVM) throws AccessDeniedException {
        // Authenticated user (trust Spring Security context only)
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));

        // Check user details account
        var currentUserDetailsAccount = userDetailsAccountService
            .findOne(currentUser.getId())
            .orElseThrow(() -> new RuntimeException("User details account not found"));
        // Validate amount
        if (transactionRecordVM.getSendAmount() == null || transactionRecordVM.getSendAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        if (currentUserDetailsAccount.getAccountBalance().compareTo(transactionRecordVM.getSendAmount()) < 0) {
            throw new IllegalStateException(INSUFFICIENT_BALANCE_MESSAGE);
        }

        // Check receiver account
        var receiverDetailsAccount = userDetailsAccountService
            .findByAccountNumber(transactionRecordVM.getReceiverAccountNumber())
            .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        // Idempotency check (HASHED key)
        String keyHash = hashIdempotencyKey(transactionRecordVM.getIdempotencyKey());

        // check idempotency using transaction reference
        var existing = idempotencyRecordRepository.findByKeyHash(keyHash);
        if (existing.isPresent()) {
            return transactionRecordMapper.fromJson(existing.get().getResponseBody());
        }

        BigDecimal receiveAmount = calculateReceiveAmount(transactionRecordVM);

        // Create transaction
        TransactionRecord transaction = new TransactionRecord();
        transaction.setCreatedAt(Instant.now());
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(transactionRecordVM.getTransactionType());
        transaction.setSendAmount(transactionRecordVM.getSendAmount());
        transaction.setReceiveAmount(receiveAmount);
        transaction.setCurrencySendAmount(transactionRecordVM.getCurrencySendAmount());
        transaction.setCurrencyReceiveAmount(transactionRecordVM.getCurrencyReceiveAmount());
        transaction.setSenderAccountNumber(currentUserDetailsAccount.getAccountNumber());
        transaction.setReceiverAccountNumber(receiverDetailsAccount.getAccountNumber());
        transaction.setUserLogin(currentUser);

        transactionRecordRepository.save(transaction);

        // Balance updates
        currentUserDetailsAccount.setAccountBalance(
            currentUserDetailsAccount.getAccountBalance().subtract(transactionRecordVM.getSendAmount())
        );
        receiverDetailsAccount.setAccountBalance(receiverDetailsAccount.getAccountBalance().add(receiveAmount));

        userDetailsAccountService.save(currentUserDetailsAccount);
        userDetailsAccountService.save(receiverDetailsAccount);

        // Save idempotency record
        IdempotencyRecord idempotency = new IdempotencyRecord();
        idempotency.setKeyHash(keyHash);
        idempotency.setEndpoint(transactionRecordVM.getEndpoint());
        idempotency.setUserId(currentUser.getId());
        idempotency.setTransactionReference(transaction.getTransactionReference());
        idempotency.setCreatedAt(Instant.now());
        idempotency.setResponseBody(transactionRecordMapper.toJson(transactionRecordMapper.toDto(transaction)));

        idempotencyRecordRepository.save(idempotency);

        return transactionRecordMapper.toDto(transaction);
    }

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
