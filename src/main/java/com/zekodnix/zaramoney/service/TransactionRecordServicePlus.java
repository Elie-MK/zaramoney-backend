package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.TransactionReceiptDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import com.zekodnix.zaramoney.web.rest.vm.WithdrawalTransactionRecordVM;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Money movement initiated by a customer. Order of checks for both flows:
 * <ol>
 *   <li>idempotent replay (per user, same payload) → return the original receipt</li>
 *   <li>KYC must be VERIFIED</li>
 *   <li>transaction PIN (own transaction, so the failed-attempt counter survives a rollback)</li>
 *   <li>then, in ONE transaction: reserve the idempotency key (a parallel duplicate blocks here and fails)</li>
 *   <li>lock both accounts (consistent order), then balance incl. fee, minimum balance, daily/monthly limits</li>
 *   <li>move the money, record fee and ledger entries, complete idempotency, notify</li>
 * </ol>
 */
@Service
public class TransactionRecordServicePlus {

    static final String SEND_ENDPOINT = "/api/plus/transaction-records/send";
    static final String WITHDRAWAL_ENDPOINT = "/api/plus/transaction-records/withdrawal";

    private final UserService userService;
    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;
    private final IdempotencyRecordServicePlus idempotencyRecordServicePlus;
    private final BankAccountServicePlus bankAccountServicePlus;
    private final TransactionValidatorService transactionValidatorService;
    private final LedgerEntryServicePlus ledgerEntryServicePlus;
    private final TransactionFeeServicePlus transactionFeeServicePlus;
    private final NotificationServicePlus notificationServicePlus;
    private final UserDetailsAccountService userDetailsAccountService;
    private final TransactionPinService transactionPinService;
    private final TransactionLimitServicePlus transactionLimitServicePlus;
    private final TransactionReceiptService transactionReceiptService;
    private final SystemAccountService systemAccountService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public TransactionRecordServicePlus(
        UserService userService,
        BankAccountService bankAccountService,
        BankAccountMapper bankAccountMapper,
        IdempotencyRecordServicePlus idempotencyRecordServicePlus,
        BankAccountServicePlus bankAccountServicePlus,
        TransactionValidatorService transactionValidatorService,
        LedgerEntryServicePlus ledgerEntryServicePlus,
        TransactionFeeServicePlus transactionFeeServicePlus,
        NotificationServicePlus notificationServicePlus,
        UserDetailsAccountService userDetailsAccountService,
        TransactionPinService transactionPinService,
        TransactionLimitServicePlus transactionLimitServicePlus,
        TransactionReceiptService transactionReceiptService,
        SystemAccountService systemAccountService,
        PlatformTransactionManager transactionManager
    ) {
        this.systemAccountService = systemAccountService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
        this.userService = userService;
        this.bankAccountService = bankAccountService;
        this.bankAccountMapper = bankAccountMapper;
        this.idempotencyRecordServicePlus = idempotencyRecordServicePlus;
        this.bankAccountServicePlus = bankAccountServicePlus;
        this.transactionValidatorService = transactionValidatorService;
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
        this.transactionFeeServicePlus = transactionFeeServicePlus;
        this.notificationServicePlus = notificationServicePlus;
        this.userDetailsAccountService = userDetailsAccountService;
        this.transactionPinService = transactionPinService;
        this.transactionLimitServicePlus = transactionLimitServicePlus;
        this.transactionReceiptService = transactionReceiptService;
    }

    /**
     * Not @Transactional on purpose: the PIN is verified in its own short transaction <b>before</b> the transfer
     * transaction opens. Nesting it (REQUIRES_NEW inside an open transaction) needs two pool connections per request
     * and can exhaust the pool under load.
     */
    public TransactionReceiptDTO createTransactionRecord(TransactionRecordVM vm) {
        User currentUser = currentUser();
        String receiverNumber = vm.getReceiverAccountNumber().trim();
        String requestHash = IdempotencyRecordServicePlus.requestHash(
            SEND_ENDPOINT,
            normalize(vm.getSenderAccountNumber()),
            receiverNumber,
            vm.getSendAmount(),
            vm.getCurrencySendAmount(),
            vm.getDescription()
        );

        var replay = findReplay(vm.getIdempotencyKey(), currentUser, SEND_ENDPOINT, requestHash);
        if (replay != null) {
            return replay;
        }

        requireKycVerified(currentUser);
        transactionPinService.verifyPin(currentUser.getId(), vm.getPin());

        return transactionTemplate.execute(status -> {
            var again = findReplayInTx(vm.getIdempotencyKey(), currentUser, SEND_ENDPOINT, requestHash);
            if (again != null) {
                return again;
            }
            var reservation = idempotencyRecordServicePlus.reserve(vm.getIdempotencyKey(), currentUser, SEND_ENDPOINT, requestHash);

            BankAccount receiver = bankAccountServicePlus
                .findCustomerAccount(receiverNumber)
                .orElseThrow(() -> ApiException.badRequest("Receiver account not found", "receivernotfound"));
            String senderNumber = resolveSenderAccountNumber(vm.getSenderAccountNumber());
            if (senderNumber.equals(receiver.getAccountNumber())) {
                throw ApiException.badRequest("You cannot send money to your own account", "sendtomyself");
            }

            LockedAccountsDTO accounts = bankAccountServicePlus.lockAccounts(senderNumber, receiver.getAccountNumber());
            requireOwnedBy(accounts.getSenderAccount(), currentUser);
            BigDecimal amount = vm.getSendAmount();
            BigDecimal fee = calculateTransactionFee(amount);

            transactionValidatorService.checkAmountValidation(amount, fee, accounts);
            transactionLimitServicePlus.checkLimits(accounts.getSenderAccount(), amount);

            TransactionRecordDTO result = transactionValidatorService.process(vm, accounts, fee);
            recordFeeAndLedger(result, accounts, amount, fee);

            TransactionReceiptDTO receipt = transactionReceiptService.buildSentReceipt(
                result,
                accounts.getSenderAccount(),
                accounts.getReceiverAccount(),
                fee
            );
            idempotencyRecordServicePlus.complete(reservation, result.getTransactionReference(), receipt);

            notificationServicePlus.createTransactionNotification(result, currentUser, result.getSendAmount(), true);
            notificationServicePlus.createTransactionNotification(result, receiver.getUser(), result.getReceiveAmount(), false);

            return receipt;
        });
    }

    /** Not @Transactional on purpose, see {@link #createTransactionRecord}. */
    public TransactionReceiptDTO withdrawalTransaction(WithdrawalTransactionRecordVM vm) {
        User currentUser = currentUser();
        String agentNumber = vm.getAgentAccountNumber().trim();
        String requestHash = IdempotencyRecordServicePlus.requestHash(
            WITHDRAWAL_ENDPOINT,
            normalize(vm.getSenderAccountNumber()),
            agentNumber,
            vm.getSendAmount()
        );

        var replay = findReplay(vm.getIdempotencyKey(), currentUser, WITHDRAWAL_ENDPOINT, requestHash);
        if (replay != null) {
            return replay;
        }

        requireKycVerified(currentUser);
        transactionPinService.verifyPin(currentUser.getId(), vm.getPin());

        return transactionTemplate.execute(status -> {
            var again = findReplayInTx(vm.getIdempotencyKey(), currentUser, WITHDRAWAL_ENDPOINT, requestHash);
            if (again != null) {
                return again;
            }
            var reservation = idempotencyRecordServicePlus.reserve(vm.getIdempotencyKey(), currentUser, WITHDRAWAL_ENDPOINT, requestHash);

            BankAccount agentAccount = bankAccountServicePlus
                .findCustomerAccount(agentNumber)
                .orElseThrow(() -> ApiException.badRequest("Agent account not found", "agentnotfound"));
            boolean isAgent = userDetailsAccountService
                .findByUserEmail(agentAccount.getUser().getLogin())
                .map(d -> Boolean.TRUE.equals(d.getIsAgent()))
                .orElse(false);
            if (!isAgent) {
                throw ApiException.badRequest("The account is not an agent account", "notanagent");
            }
            String senderNumber = resolveSenderAccountNumber(vm.getSenderAccountNumber());
            if (senderNumber.equals(agentAccount.getAccountNumber())) {
                throw ApiException.badRequest("Sender and agent accounts must be different", "sendtomyself");
            }

            LockedAccountsDTO accounts = bankAccountServicePlus.lockAccounts(senderNumber, agentAccount.getAccountNumber());
            requireOwnedBy(accounts.getSenderAccount(), currentUser);
            BigDecimal amount = vm.getSendAmount();
            BigDecimal fee = calculateWithdrawalFee(amount);

            transactionValidatorService.checkAmountValidation(amount, fee, accounts);
            transactionLimitServicePlus.checkLimits(accounts.getSenderAccount(), amount);

            TransactionRecordDTO result = transactionValidatorService.processWithdrawal(vm, accounts, fee);
            recordFeeAndLedger(result, accounts, amount, fee);

            TransactionReceiptDTO receipt = transactionReceiptService.buildSentReceipt(
                result,
                accounts.getSenderAccount(),
                accounts.getReceiverAccount(),
                fee
            );
            idempotencyRecordServicePlus.complete(reservation, result.getTransactionReference(), receipt);

            notificationServicePlus.createTransactionNotification(result, currentUser, result.getSendAmount(), true);
            notificationServicePlus.createTransactionNotification(result, agentAccount.getUser(), result.getReceiveAmount(), false);

            return receipt;
        });
    }

    /** Idempotent replay, in its own read-only transaction. */
    private TransactionReceiptDTO findReplay(String key, User user, String endpoint, String requestHash) {
        return readOnlyTemplate.execute(status -> findReplayInTx(key, user, endpoint, requestHash));
    }

    private TransactionReceiptDTO findReplayInTx(String key, User user, String endpoint, String requestHash) {
        return idempotencyRecordServicePlus
            .findCompleted(key, user, endpoint, requestHash)
            .map(transactionReceiptService::findByReferenceForCurrentUser)
            .orElse(null);
    }

    /** Only the current user's own transactions. */
    @Transactional(readOnly = true)
    public Page<TransactionReceiptDTO> getAllTransactionRecords(Pageable pageable) {
        return transactionReceiptService.findAllForCurrentUser(pageable);
    }

    /** 404 if the transaction is not the current user's. */
    @Transactional(readOnly = true)
    public TransactionReceiptDTO getTransactionRecord(Long id) {
        return transactionReceiptService.findOneForCurrentUser(id);
    }

    /**
     * Balanced ledger for a transfer / withdrawal (the balances of sender and receiver were already moved by the processor):
     * <pre>
     *   DEBIT  sender        amount + fee
     *   CREDIT receiver      amount
     *   CREDIT fee revenue   fee          (only when fee > 0; the SYSTEM fee account is locked and credited here, last)
     * </pre>
     */
    private void recordFeeAndLedger(TransactionRecordDTO result, LockedAccountsDTO accounts, BigDecimal amount, BigDecimal fee) {
        transactionFeeServicePlus.createTransactionFee(fee, result);
        var entries = new java.util.ArrayList<LedgerEntryDTO>();
        entries.add(
            ledgerEntryServicePlus.buildEntry(result, bankAccountMapper.toDto(accounts.getSenderAccount()), amount.add(fee), EntryType.DEBIT)
        );
        entries.add(ledgerEntryServicePlus.buildEntry(result, bankAccountMapper.toDto(accounts.getReceiverAccount()), amount, EntryType.CREDIT));
        if (fee.signum() > 0) {
            BankAccount feeAccount = systemAccountService.lockFeeRevenueAccount();
            feeAccount.setBalance(feeAccount.getBalance().add(fee).setScale(2, RoundingMode.HALF_UP));
            entries.add(ledgerEntryServicePlus.buildEntry(result, bankAccountMapper.toDto(feeAccount), fee, EntryType.CREDIT));
        }
        ledgerEntryServicePlus.validateDoubleEntry(entries);
    }

    private User currentUser() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));
    }

    /**
     * The account to debit: the one the app asked for ({@code senderAccountNumber}), which must be one of the current
     * user's CUSTOMER accounts; without it, the user's only account (400 if they have several, so we never pick one arbitrarily).
     */
    private String resolveSenderAccountNumber(String requested) {
        List<String> own = bankAccountService
            .findByUserIsCurrentUser()
            .stream()
            .filter(a -> a.getAccountType() == null || a.getAccountType() == BankAccountType.CUSTOMER)
            .map(a -> a.getAccountNumber())
            .toList();
        String wanted = normalize(requested);
        if (!wanted.isEmpty()) {
            if (!own.contains(wanted)) {
                throw ApiException.badRequest("This sender account is not one of your accounts", "senderaccountnotfound");
            }
            return wanted;
        }
        if (own.isEmpty()) {
            throw ApiException.badRequest("You don't have an account yet", "noaccount");
        }
        if (own.size() > 1) {
            throw ApiException.badRequest("You have several accounts: choose the sender account", "senderaccountrequired");
        }
        return own.get(0);
    }

    /** Checked on the row that is actually locked and debited. */
    private static void requireOwnedBy(BankAccount account, User user) {
        if (account.getUser() == null || !Objects.equals(account.getUser().getId(), user.getId())) {
            throw ApiException.forbidden("This account is not yours", "accountnotowned");
        }
    }

    private static String normalize(String accountNumber) {
        return accountNumber == null ? "" : accountNumber.trim();
    }

    private void requireKycVerified(User user) {
        var status = userDetailsAccountService.findByUserEmail(user.getEmail()).map(d -> d.getKycStatus()).orElse(null);
        if (status != KycStatus.VERIFIED) {
            throw ApiException.forbidden(
                status == KycStatus.REJECTED
                    ? "Your identity verification was rejected, contact support"
                    : "Your identity verification is not approved yet",
                "kycnotverified"
            );
        }
    }

    // Fee schedules: unchanged in Phase 2 (tier review is a Phase 3 item)
    private BigDecimal calculateWithdrawalFee(BigDecimal amount) {
        BigDecimal min = BigDecimal.valueOf(1);
        BigDecimal tier1Max = BigDecimal.valueOf(5);
        BigDecimal tier2Max = BigDecimal.valueOf(20);
        BigDecimal tier3Max = BigDecimal.valueOf(50);

        if (amount.compareTo(min) < 0) {
            throw ApiException.badRequest("Amount must be at least 1", "invalidamount");
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
            return BigDecimal.ZERO.setScale(2);
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
}
