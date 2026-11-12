package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Money movement initiated by a customer: P2P transfer and withdrawal to an agent share ONE flow ({@link #execute}):
 * <ol>
 *   <li>idempotent replay (per user, same payload) → return the original receipt</li>
 *   <li>KYC must be VERIFIED</li>
 *   <li>transaction PIN (own transaction, so the failed-attempt counter survives a rollback)</li>
 *   <li>then, in ONE transaction: reserve the idempotency key (a parallel duplicate blocks here and fails)</li>
 *   <li>resolve the counterparty and the sender account, lock both (consistent order), check ownership, status and
 *   currency, then balance incl. fee, minimum balance, daily/monthly limits</li>
 *   <li>move the money, record fee and ledger entries (fee → SYSTEM fee revenue account), complete idempotency,
 *   save notifications (pushes go out after commit)</li>
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
    private final FeeSchedule feeSchedule;
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
        FeeSchedule feeSchedule,
        PlatformTransactionManager transactionManager
    ) {
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
        this.systemAccountService = systemAccountService;
        this.feeSchedule = feeSchedule;
    }

    /**
     * Everything that differs between a transfer and a withdrawal. {@code counterparty} runs inside the transaction and
     * returns the (unlocked) receiving CUSTOMER account or throws the flow-specific 4xx.
     */
    private record MoneyRequest(
        TransactionType type,
        String endpoint,
        String idempotencyKey,
        String pin,
        String requestHash,
        String senderAccountNumber,
        BigDecimal amount,
        Currency sendCurrency,
        Currency receiveCurrency,
        String description,
        Supplier<BankAccount> counterparty
    ) {}

    /** {@code POST /send}. The transaction type is always TRANSFER (the client field is ignored). */
    public TransactionReceiptDTO createTransactionRecord(TransactionRecordVM vm) {
        String receiverNumber = vm.getReceiverAccountNumber().trim();
        String requestHash = IdempotencyRecordServicePlus.requestHash(
            SEND_ENDPOINT,
            normalize(vm.getSenderAccountNumber()),
            receiverNumber,
            vm.getSendAmount(),
            vm.getCurrencySendAmount(),
            vm.getDescription()
        );
        return execute(
            new MoneyRequest(
                TransactionType.TRANSFER,
                SEND_ENDPOINT,
                vm.getIdempotencyKey(),
                vm.getPin(),
                requestHash,
                vm.getSenderAccountNumber(),
                vm.getSendAmount(),
                vm.getCurrencySendAmount(),
                vm.getCurrencyReceiveAmount(),
                vm.getDescription(),
                () ->
                    bankAccountServicePlus
                        .findCustomerAccount(receiverNumber)
                        .orElseThrow(() -> ApiException.badRequest("Receiver account not found", "receivernotfound"))
            )
        );
    }

    /** {@code POST /withdrawal}: the receiver must be an agent. */
    public TransactionReceiptDTO withdrawalTransaction(WithdrawalTransactionRecordVM vm) {
        String agentNumber = vm.getAgentAccountNumber().trim();
        String requestHash = IdempotencyRecordServicePlus.requestHash(
            WITHDRAWAL_ENDPOINT,
            normalize(vm.getSenderAccountNumber()),
            agentNumber,
            vm.getSendAmount()
        );
        return execute(
            new MoneyRequest(
                TransactionType.WITHDRAWAL,
                WITHDRAWAL_ENDPOINT,
                vm.getIdempotencyKey(),
                vm.getPin(),
                requestHash,
                vm.getSenderAccountNumber(),
                vm.getSendAmount(),
                null,
                null,
                null,
                () -> {
                    BankAccount agent = bankAccountServicePlus
                        .findCustomerAccount(agentNumber)
                        .orElseThrow(() -> ApiException.badRequest("Agent account not found", "agentnotfound"));
                    boolean isAgent = userDetailsAccountService
                        .findByUserEmail(agent.getUser().getLogin())
                        .map(d -> Boolean.TRUE.equals(d.getIsAgent()))
                        .orElse(false);
                    if (!isAgent) {
                        throw ApiException.badRequest("The account is not an agent account", "notanagent");
                    }
                    return agent;
                }
            )
        );
    }

    /**
     * Not @Transactional on purpose: the PIN is verified in its own short transaction <b>before</b> the money
     * transaction opens. Nesting it (REQUIRES_NEW inside an open transaction) needs two pool connections per request
     * and can exhaust the pool under load.
     */
    private TransactionReceiptDTO execute(MoneyRequest req) {
        User currentUser = currentUser();

        var replay = findReplay(req.idempotencyKey(), currentUser, req.endpoint(), req.requestHash());
        if (replay != null) {
            return replay;
        }

        requireKycVerified(currentUser);
        transactionPinService.verifyPin(currentUser.getId(), req.pin());

        return transactionTemplate.execute(status -> {
            var again = findReplayInTx(req.idempotencyKey(), currentUser, req.endpoint(), req.requestHash());
            if (again != null) {
                return again;
            }
            var reservation = idempotencyRecordServicePlus.reserve(req.idempotencyKey(), currentUser, req.endpoint(), req.requestHash());

            BankAccount counterparty = req.counterparty().get();
            String senderNumber = resolveSenderAccountNumber(req.senderAccountNumber());
            if (senderNumber.equals(counterparty.getAccountNumber())) {
                throw ApiException.badRequest("You cannot send money to your own account", "sendtomyself");
            }

            LockedAccountsDTO accounts = bankAccountServicePlus.lockAccounts(senderNumber, counterparty.getAccountNumber());
            requireOwnedBy(accounts.getSenderAccount(), currentUser);
            transactionValidatorService.checkAccounts(accounts, req.sendCurrency(), req.receiveCurrency());

            BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = req.type() == TransactionType.WITHDRAWAL ? feeSchedule.withdrawalFee(amount) : feeSchedule.transferFee(amount);
            transactionValidatorService.checkAmountValidation(amount, fee, accounts);
            transactionLimitServicePlus.checkLimits(accounts.getSenderAccount(), amount);

            String description = req.type() == TransactionType.WITHDRAWAL
                ? "Retrait de " + amount + " " + accounts.getSenderAccount().getCurrency()
                : req.description();
            TransactionRecordDTO result = transactionValidatorService.move(req.type(), accounts, amount, fee, description);
            recordFeeAndLedger(result, accounts, amount, fee);

            TransactionReceiptDTO receipt = transactionReceiptService.buildSentReceipt(
                result,
                accounts.getSenderAccount(),
                accounts.getReceiverAccount(),
                fee
            );
            idempotencyRecordServicePlus.complete(reservation, result.getTransactionReference(), receipt);

            notificationServicePlus.createTransactionNotification(result, currentUser, result.getSendAmount(), true);
            notificationServicePlus.createTransactionNotification(result, counterparty.getUser(), result.getReceiveAmount(), false);
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
        var entries = new ArrayList<LedgerEntryDTO>();
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
}
