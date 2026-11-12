package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.dto.DepositDto;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office money operations (admin only).
 *
 * <p>Admin deposit = cash received by ZaraMoney outside the app, credited to a customer wallet. In ONE transaction:
 * <ol>
 *   <li>idempotent replay (key scoped to the admin + request hash) → return the original deposit</li>
 *   <li>reserve the idempotency key (flushed: a parallel double click blocks on the unique index and gets 409)</li>
 *   <li>lock the customer account row, check it is an ACTIVE CUSTOMER account owned by {@code userEmail}</li>
 *   <li>lock the SYSTEM settlement account (always after the customer row: 2512... before 9999...)</li>
 *   <li>DEBIT settlement / CREDIT customer, DEPOSIT transaction, balanced ledger entries, complete idempotency, notify</li>
 * </ol>
 * The admin who made the deposit is recorded on the idempotency record (user id + transaction reference) and logged.
 */
@Service
public class BackOfficeService {

    static final String DEPOSIT_ENDPOINT = "/api/admin/backoffice/user/deposit";

    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeService.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;
    private final SystemAccountService systemAccountService;
    private final IdempotencyRecordServicePlus idempotencyRecordServicePlus;
    private final TransactionRecordService transactionRecordService;
    private final TransactionRecordRepository transactionRecordRepository;
    private final TransactionRecordMapper transactionRecordMapper;
    private final TransactionValidatorService transactionValidatorService;
    private final LedgerEntryServicePlus ledgerEntryServicePlus;
    private final NotificationServicePlus notificationServicePlus;

    private final AuditService auditService;

    public BackOfficeService(
        UserService userService,
        UserRepository userRepository,
        BankAccountRepository bankAccountRepository,
        BankAccountMapper bankAccountMapper,
        SystemAccountService systemAccountService,
        IdempotencyRecordServicePlus idempotencyRecordServicePlus,
        TransactionRecordService transactionRecordService,
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper,
        TransactionValidatorService transactionValidatorService,
        LedgerEntryServicePlus ledgerEntryServicePlus,
        NotificationServicePlus notificationServicePlus,
        AuditService auditService
    ) {
        this.auditService = auditService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.bankAccountMapper = bankAccountMapper;
        this.systemAccountService = systemAccountService;
        this.idempotencyRecordServicePlus = idempotencyRecordServicePlus;
        this.transactionRecordService = transactionRecordService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordMapper = transactionRecordMapper;
        this.transactionValidatorService = transactionValidatorService;
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
        this.notificationServicePlus = notificationServicePlus;
    }

    @Transactional
    public TransactionRecordDTO addMoneyToUserAccount(DepositDto depositDto) {
        // Defense in depth: /api/admin/** is already ROLE_ADMIN (URL rule + @PreAuthorize on the resource)
        if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            throw ApiException.forbidden("Only admins can make deposits", "forbidden");
        }
        User admin = userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));

        BigDecimal amount = depositDto.getAmount().setScale(2, RoundingMode.UNNECESSARY); // @Digits guarantees <= 2 decimals
        String accountNumber = depositDto.getUserAccountNumber().trim();
        String email = depositDto.getUserEmail().trim().toLowerCase(Locale.ROOT);
        String requestHash = IdempotencyRecordServicePlus.requestHash(DEPOSIT_ENDPOINT, accountNumber, email, amount);

        var replay = idempotencyRecordServicePlus.findCompleted(depositDto.getIdempotencyKey(), admin, DEPOSIT_ENDPOINT, requestHash);
        if (replay.isPresent()) {
            return transactionRecordRepository
                .findOneByTransactionReference(replay.orElseThrow())
                .map(transactionRecordMapper::toDto)
                .orElseThrow(() -> new IllegalStateException("Completed deposit without transaction: " + replay.orElseThrow()));
        }
        var reservation = idempotencyRecordServicePlus.reserve(depositDto.getIdempotencyKey(), admin, DEPOSIT_ENDPOINT, requestHash);

        User owner = userRepository
            .findOneByLogin(email)
            .orElseThrow(() -> ApiException.notFound("No user with this e-mail", "usernotfound"));

        BankAccount customer = bankAccountRepository
            .findByAccountNumberForUpdate(accountNumber)
            .filter(a -> a.getAccountType() == BankAccountType.CUSTOMER && a.getUser() != null)
            .orElseThrow(() -> ApiException.notFound("Customer account not found", "accountnotfound"));
        if (!Objects.equals(customer.getUser().getId(), owner.getId())) {
            throw ApiException.badRequest("The account does not belong to this user", "accountusermismatch");
        }
        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.badRequest("The account is not active", "accountnotactive");
        }
        if (customer.getCurrency() != Currency.USD) {
            // the settlement account is USD; other currencies need their own settlement account (Phase 4)
            throw ApiException.badRequest("Deposits are only supported in USD", "unsupportedcurrency");
        }
        BankAccount settlement = systemAccountService.lockSettlementAccount();

        settlement.setBalance(settlement.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
        customer.setBalance(customer.getBalance().add(amount).setScale(2, RoundingMode.HALF_UP));

        var settlementDto = bankAccountMapper.toDto(settlement);
        var customerDto = bankAccountMapper.toDto(customer);
        Instant now = Instant.now();

        TransactionRecordDTO tx = new TransactionRecordDTO();
        tx.setTransactionReference(transactionValidatorService.generateTransactionReference());
        tx.setTransactionType(TransactionType.DEPOSIT);
        tx.setSendAmount(amount);
        tx.setReceiveAmount(amount);
        tx.setCurrencySendAmount(customer.getCurrency());
        tx.setCurrencyReceiveAmount(customer.getCurrency());
        tx.setDescription("Dépôt de " + amount + " " + customer.getCurrency());
        tx.setSender(settlementDto);
        tx.setReceiver(customerDto);
        tx.setExchangeRate(BigDecimal.ONE);
        tx.setFee(BigDecimal.ZERO);
        tx.setCreatedAt(now);
        tx.setTransactionDate(now);
        tx.setTransactionStatus(TransactionStatus.COMPLETED);
        tx.setFraudStatus(FraudStatus.CLEAN);
        tx.setRiskScore(0);
        TransactionRecordDTO saved = transactionRecordService.save(tx);

        var debit = ledgerEntryServicePlus.buildEntry(saved, settlementDto, amount, EntryType.DEBIT);
        var credit = ledgerEntryServicePlus.buildEntry(saved, customerDto, amount, EntryType.CREDIT);
        ledgerEntryServicePlus.validateDoubleEntry(debit, credit);

        idempotencyRecordServicePlus.complete(reservation, saved.getTransactionReference(), saved.getId());
        notificationServicePlus.createTransactionNotification(saved, owner, amount, false);

        auditService.record(
            AuditAction.DEPOSIT,
            "TRANSACTION",
            saved.getTransactionReference(),
            "amount=" + amount + " " + customer.getCurrency() + " account=" + accountNumber + " owner=" + email
        );
        return saved;
    }
}
