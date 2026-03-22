package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.exception.UserDetailsAccountNotFoundException;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
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
import java.util.*;
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

    private final TransactionRecordMapper transactionRecordMapper;
    private final UserService userService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;

    public TransactionRecordServicePlus(
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper,
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        IdempotencyRecordRepository idempotencyRecordRepository,
        PasswordEncoder passwordEncoder,
        TransactionRecordService transactionRecordService,
        BankAccountService bankAccountService,
        BankAccountMapper bankAccountMapper
    ) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.userService = userService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionRecordService = transactionRecordService;
        this.bankAccountService = bankAccountService;
        this.bankAccountMapper = bankAccountMapper;
    }

    @Transactional
    public TransactionRecordDTO createTransactionRecord(TransactionRecordVM vm) throws AccessDeniedException, JsonProcessingException {
        // 1. Validate idempotency key
        if (vm.getIdempotencyKey() == null) {
            throw new BadRequestAlertException("Missing idempotency key", "transaction", "idempotencykeynull");
        }

        // 2. Check for existing transaction (idempotency)
        String keyHash = hashIdempotencyKey(vm.getIdempotencyKey());
        var existing = idempotencyRecordRepository.findByKeyHash(keyHash);
        if (existing.isPresent()) {
            return transactionRecordMapper.fromJson(existing.get().getResponseBody());
        }

        // 3. Get authenticated user
        var currentUser = userService.getUserWithAuthorities().orElseThrow(() -> new AccessDeniedException("Unauthorized"));

        // 4. Verify transaction password BEFORE DB locks
        validateTransactionPassword(vm, currentUser);

        // 5. Fetch accounts for update with consistent lock ordering
        var accounts = fetchAccountsWithLock(vm.getSenderAccountNumber(), vm.getReceiverAccountNumber());
        var sender = accounts.get("sender");
        var receiver = accounts.get("receiver");

        // 6. Ownership check
        if (!sender.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not allowed to use this account");
        }

        // 7. Business validations
        validateTransactionBusinessRules(vm, sender, receiver);

        // 8. Reserve idempotency
        IdempotencyRecord idempotency = reserveIdempotency(vm, keyHash, currentUser);

        // 9. Calculate receive amount
        BigDecimal receiveAmount = calculateReceiveAmount(vm);

        // 10. Update balances
        updateBalances(sender, receiver, vm.getSendAmount(), receiveAmount);

        // 11. Map updated accounts to DTOs
        BankAccountDTO senderDto = bankAccountMapper.toDto(sender);
        BankAccountDTO receiverDto = bankAccountMapper.toDto(receiver);

        // 12. Save updated accounts
        bankAccountService.save(senderDto);
        bankAccountService.save(receiverDto);

        // 13. Create and save transaction record
        TransactionRecord transaction = getTransactionRecord(vm, receiveAmount, sender, receiver);
        TransactionRecordDTO savedTransaction = transactionRecordService.save(transactionRecordMapper.toDto(transaction));
        savedTransaction.setSender(senderDto);
        savedTransaction.setReceiver(receiverDto);

        // 14. Complete idempotency
        completeIdempotency(idempotency, savedTransaction);

        // 15. Return result
        return savedTransaction;
    }

    private void validateTransactionPassword(TransactionRecordVM vm, User currentUser) {
        if (vm.getPassword() == null || !passwordEncoder.matches(vm.getPassword(), currentUser.getPassword())) {
            throw new BadRequestAlertException("Invalid transaction password", "transaction", "invalidpassword");
        }
    }

    private Map<String, BankAccount> fetchAccountsWithLock(String senderAcc, String receiverAcc) {
        BankAccount firstLock, secondLock;

        if (senderAcc.compareTo(receiverAcc) < 0) {
            firstLock = bankAccountService
                .findByAccountNumberForUpdate(senderAcc)
                .orElseThrow(() -> new UserDetailsAccountNotFoundException("Sender account not found"));
            secondLock = bankAccountService
                .findByAccountNumberForUpdate(receiverAcc)
                .orElseThrow(() -> new BadRequestAlertException("Receiver account not found", "transaction", "receivernotfound"));
        } else {
            secondLock = bankAccountService
                .findByAccountNumberForUpdate(receiverAcc)
                .orElseThrow(() -> new BadRequestAlertException("Receiver account not found", "transaction", "receivernotfound"));
            firstLock = bankAccountService
                .findByAccountNumberForUpdate(senderAcc)
                .orElseThrow(() -> new UserDetailsAccountNotFoundException("Sender account not found"));
        }

        Map<String, BankAccount> map = new HashMap<>();
        map.put("sender", firstLock);
        map.put("receiver", secondLock);
        return map;
    }

    private void validateTransactionBusinessRules(TransactionRecordVM vm, BankAccount sender, BankAccount receiver) {
        // Amount validations
        if (vm.getSendAmount() == null || vm.getSendAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Invalid amount", "transaction", "invalidamount");
        }

        if (sender.getBalance().compareTo(vm.getSendAmount()) < 0) {
            throw new BadRequestAlertException("Insufficient balance", "transaction", "insufficientbalance");
        }

        if (sender.getBalance().subtract(vm.getSendAmount()).compareTo(MIN_BALANCE) < 0) {
            throw new BadRequestAlertException(
                "Transaction denied: account must maintain a minimum balance of " + MIN_BALANCE,
                "transaction",
                "minimumbalance"
            );
        }

        // Self-transfer check
        if (sender.getAccountNumber().equals(receiver.getAccountNumber())) {
            throw new BadRequestAlertException("Cannot transfer to same account", "transaction", "selftransfer");
        }

        // Currency validations
        if (vm.getCurrencySendAmount() == null || vm.getCurrencyReceiveAmount() == null) {
            throw new BadRequestAlertException("Currency must be specified", "transaction", "currencyrequired");
        }

        if (!receiver.getCurrency().equals(vm.getCurrencyReceiveAmount())) {
            throw new BadRequestAlertException(
                "Receiver account must be in " + vm.getCurrencyReceiveAmount(),
                "transaction",
                "receivercurrency"
            );
        }
    }

    private void updateBalances(BankAccount sender, BankAccount receiver, BigDecimal sendAmount, BigDecimal receiveAmount) {
        // Use explicit rounding to avoid precision issues
        sender.setBalance(sender.getBalance().subtract(sendAmount).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(receiveAmount).setScale(2, RoundingMode.HALF_UP));
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

    private void completeIdempotency(IdempotencyRecord idempotency, TransactionRecordDTO transaction) throws JsonProcessingException {
        idempotency.setTransactionReference(transaction.getTransactionReference());
        idempotency.setResponseStatus(1); // success

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String responseJson = mapper.writeValueAsString(transaction);

        idempotency.setResponseBody(responseJson);
        idempotency.responseStatus(1);

        idempotencyRecordRepository.save(idempotency);
    }

    private TransactionRecord getTransactionRecord(
        TransactionRecordVM transactionRecordVM,
        BigDecimal receiveAmount,
        BankAccount currentUser,
        BankAccount receiverBankAccount
    ) {
        // Create transaction
        TransactionRecord transaction = new TransactionRecord();
        transaction.setTransactionReference(generateTransactionReference());
        transaction.setTransactionType(transactionRecordVM.getTransactionType());
        transaction.setSendAmount(transactionRecordVM.getSendAmount());
        transaction.setReceiveAmount(receiveAmount);
        transaction.setCurrencySendAmount(transactionRecordVM.getCurrencySendAmount());
        transaction.setCurrencyReceiveAmount(transactionRecordVM.getCurrencyReceiveAmount());
        transaction.setDescription(transactionRecordVM.getDescription());
        transaction.setSender(currentUser);
        transaction.setReceiver(receiverBankAccount);

        // default risk score
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDate(Instant.now());
        transaction.setFraudStatus(FraudStatus.CLEAN);
        transaction.setRiskScore(0);

        return transaction;
    }

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
