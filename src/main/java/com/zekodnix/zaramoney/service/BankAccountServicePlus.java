package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankAccountServicePlus {

    private static final Logger LOG = LoggerFactory.getLogger(BankAccountServicePlus.class);

    private static final String ACCOUNT_NUMBER_PREFIX = "2512";
    private static final int ACCOUNT_NUMBER_LENGTH = 12;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankAccountService bankAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionLimitServicePlus transactionLimitServicePlus;
    private final SignupBonusService signupBonusService;
    private final TransactionRecordRepository transactionRecordRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final UserRepository userRepository;

    private final AuditService auditService;

    public BankAccountServicePlus(
        BankAccountService bankAccountService,
        BankAccountRepository bankAccountRepository,
        TransactionLimitServicePlus transactionLimitServicePlus,
        SignupBonusService signupBonusService,
        TransactionRecordRepository transactionRecordRepository,
        LedgerEntryRepository ledgerEntryRepository,
        TransactionLimitRepository transactionLimitRepository,
        UserRepository userRepository,
        AuditService auditService
    ) {
        this.auditService = auditService;
        this.bankAccountService = bankAccountService;
        this.bankAccountRepository = bankAccountRepository;
        this.transactionLimitServicePlus = transactionLimitServicePlus;
        this.signupBonusService = signupBonusService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionLimitRepository = transactionLimitRepository;
        this.userRepository = userRepository;
    }

    public LockedAccountsDTO lockAccounts(String senderAcc, String receiverAcc) {
        // Always lock in consistent order (important for deadlocks)
        if (senderAcc.compareTo(receiverAcc) < 0) {
            return new LockedAccountsDTO(lock(senderAcc), lock(receiverAcc));
        } else {
            var receiver = lock(receiverAcc);
            var sender = lock(senderAcc);
            return new LockedAccountsDTO(sender, receiver);
        }
    }

    private BankAccount lock(String accountNumber) {
        return bankAccountService
            .findByAccountNumberForUpdate(accountNumber)
            .orElseThrow(() -> ApiException.notFound("Account not found", "accountnotfound"));
    }

    /** True only for an existing CUSTOMER account (system accounts are never reported). */
    @Transactional(readOnly = true)
    public Boolean verifyAccount(String accountNumber) {
        return findCustomerAccount(accountNumber).isPresent();
    }

    /** A customer account usable as a transfer counterparty; SYSTEM accounts are treated as non-existent. */
    @Transactional(readOnly = true)
    public Optional<BankAccount> findCustomerAccount(String accountNumber) {
        if (accountNumber == null) {
            return Optional.empty();
        }
        return bankAccountRepository
            .findByAccountNumber(accountNumber.trim())
            .filter(account -> account.getAccountType() == BankAccountType.CUSTOMER && account.getUser() != null);
    }

    /** Must run inside the caller's transaction: account, limits and signup bonus are created atomically. */
    @Transactional
    public BankAccountDTO createBankAccount(UserDTO user) {
        var bankAccountDTO = new BankAccountDTO();
        bankAccountDTO.setUser(user);
        bankAccountDTO.setBalance(BigDecimal.ZERO.setScale(2));
        bankAccountDTO.setAccountNumber(generateUniqueAccountNumber());
        bankAccountDTO.setCurrency(Currency.USD);
        bankAccountDTO.setStatus(AccountStatus.ACTIVE);
        bankAccountDTO.setAccountType(BankAccountType.CUSTOMER);
        bankAccountDTO.setCreatedAt(Instant.now());

        var account = bankAccountService.save(bankAccountDTO);
        transactionLimitServicePlus.createBankAccountLimit(account);

        // Welcome bonus, funded by the SYSTEM promo account through the ledger
        signupBonusService.grant(account.getAccountNumber());

        return bankAccountService.findOne(account.getId()).orElse(account);
    }

    // ---------------------------------------------------------------------------------------------- admin CRUD
    // /api/bank-accounts must never move or reassign money outside the ledger:
    //  - balance is read-only: create starts at 0.00, PUT keeps it, PATCH drops it (a supplied value is logged);
    //  - an account "in use" (non-zero balance, or any transaction / ledger entry) keeps its accountNumber, currency,
    //    owner and accountType (409 accountinuse), and cannot be deleted (409 accountinuse);
    //  - the row is locked first (like a transfer), so a stale read can never write an old balance back.

    /** Admin create: always starts at 0.00 (fund it with an admin deposit). */
    @Transactional
    public BankAccountDTO adminCreate(BankAccountDTO dto) {
        warnIfBalanceSupplied(dto, BigDecimal.ZERO);
        dto.setBalance(BigDecimal.ZERO.setScale(2));
        BankAccountDTO saved = bankAccountService.save(dto);
        auditService.record(
            AuditAction.BANK_ACCOUNT_CREATED,
            "BANK_ACCOUNT",
            saved.getAccountNumber(),
            "currency=" + saved.getCurrency() + " status=" + saved.getStatus() + " type=" + saved.getAccountType() + " owner=" + ownerId(saved)
        );
        return saved;
    }

    /** Admin full update (PUT): every field except the balance, which keeps its current value. */
    @Transactional
    public BankAccountDTO adminUpdate(BankAccountDTO dto) {
        BankAccount locked = lockById(dto.getId());
        warnIfBalanceSupplied(dto, locked.getBalance());
        dto.setBalance(locked.getBalance());
        if (dto.getAccountType() == null) {
            dto.setAccountType(locked.getAccountType()); // optional in the DTO: absent = unchanged (the column is NOT NULL)
        }
        Long newOwnerId = dto.getUser() == null ? null : dto.getUser().getId();
        requireIdentityUnchangedIfInUse(locked, dto.getAccountNumber(), dto.getCurrency(), dto.getAccountType(), true, newOwnerId);
        if (newOwnerId != null) {
            requireUser(newOwnerId); // 400 instead of a foreign-key 500
        }
        String changes = changes(locked, dto, true);
        BankAccountDTO saved = bankAccountService.update(dto);
        auditUpdate(saved.getAccountNumber(), changes);
        return saved;
    }

    /** Admin partial update (PATCH): a balance in the request is dropped; absent fields are unchanged. */
    @Transactional
    public Optional<BankAccountDTO> adminPartialUpdate(BankAccountDTO dto) {
        BankAccount locked = lockById(dto.getId());
        warnIfBalanceSupplied(dto, locked.getBalance());
        dto.setBalance(null); // partialUpdate ignores null fields
        boolean ownerSupplied = dto.getUser() != null;
        requireIdentityUnchangedIfInUse(
            locked,
            dto.getAccountNumber(),
            dto.getCurrency(),
            dto.getAccountType(),
            ownerSupplied,
            ownerSupplied ? dto.getUser().getId() : null
        );
        String changes = changes(locked, dto, ownerSupplied);
        if (ownerSupplied) {
            // Never let the mapper touch the owner: MapStruct's partialUpdate copies the DTO's id/login INTO the
            // current owner's managed User (renaming that user) instead of switching the reference.
            locked.setUser(requireUser(dto.getUser().getId()));
            dto.setUser(null);
        }
        String accountNumber = locked.getAccountNumber();
        Optional<BankAccountDTO> saved = bankAccountService.partialUpdate(dto);
        saved.ifPresent(s -> auditUpdate(accountNumber, changes));
        return saved;
    }

    /** Admin delete: only an unused account (zero balance, no transaction, no ledger entry); its limits go with it. */
    @Transactional
    public void adminDelete(Long id) {
        BankAccount locked = lockById(id);
        if (isInUse(locked)) {
            throw ApiException.conflict("An account with a balance or a transaction history cannot be deleted", "accountinuse");
        }
        transactionLimitRepository.findOneByAccountId(id).ifPresent(transactionLimitRepository::delete);
        String accountNumber = locked.getAccountNumber();
        String owner = locked.getUser() == null ? null : String.valueOf(locked.getUser().getId());
        bankAccountRepository.delete(locked);
        auditService.record(AuditAction.BANK_ACCOUNT_DELETED, "BANK_ACCOUNT", accountNumber, "owner=" + owner);
    }

    /** null = "not supplied" for PATCH (except the owner, see {@code ownerSupplied}). */
    private void requireIdentityUnchangedIfInUse(
        BankAccount locked,
        String accountNumber,
        Currency currency,
        BankAccountType accountType,
        boolean ownerSupplied,
        Long newOwnerId
    ) {
        Long currentOwnerId = locked.getUser() == null ? null : locked.getUser().getId();
        boolean changes =
            (accountNumber != null && !accountNumber.equals(locked.getAccountNumber())) ||
            (currency != null && currency != locked.getCurrency()) ||
            (accountType != null && accountType != locked.getAccountType()) ||
            (ownerSupplied && !Objects.equals(newOwnerId, currentOwnerId));
        if (changes && isInUse(locked)) {
            throw ApiException.conflict(
                "accountNumber, currency, owner and accountType cannot change once the account has a balance or transactions",
                "accountinuse"
            );
        }
    }

    private User requireUser(Long userId) {
        return Optional.ofNullable(userId)
            .flatMap(userRepository::findById)
            .orElseThrow(() -> ApiException.badRequest("Owner not found", "usernotfound"));
    }

    /** "status ACTIVE->SUSPENDED, currency USD->TND" for the fields that change (null in the DTO = unchanged unless owner). */
    private static String changes(BankAccount before, BankAccountDTO after, boolean ownerSupplied) {
        List<String> c = new ArrayList<>();
        diff(c, "accountNumber", before.getAccountNumber(), after.getAccountNumber());
        diff(c, "status", before.getStatus(), after.getStatus());
        diff(c, "currency", before.getCurrency(), after.getCurrency());
        diff(c, "accountType", before.getAccountType(), after.getAccountType());
        if (ownerSupplied) {
            Long beforeOwner = before.getUser() == null ? null : before.getUser().getId();
            Long afterOwner = after.getUser() == null ? null : after.getUser().getId();
            if (!Objects.equals(beforeOwner, afterOwner)) {
                c.add("owner " + beforeOwner + "->" + afterOwner);
            }
        }
        return c.isEmpty() ? "no change" : String.join(", ", c);
    }

    private static void diff(List<String> c, String field, Object before, Object after) {
        if (after != null && !after.equals(before)) {
            c.add(field + " " + before + "->" + after);
        }
    }

    private void auditUpdate(String accountNumber, String changes) {
        auditService.record(AuditAction.BANK_ACCOUNT_UPDATED, "BANK_ACCOUNT", accountNumber, changes);
    }

    private static Long ownerId(BankAccountDTO dto) {
        return dto.getUser() == null ? null : dto.getUser().getId();
    }

    private boolean isInUse(BankAccount account) {
        return (
            account.getBalance().signum() != 0 ||
            transactionRecordRepository.existsBySender_IdOrReceiver_Id(account.getId(), account.getId()) ||
            ledgerEntryRepository.existsByAccount_Id(account.getId())
        );
    }

    private BankAccount lockById(Long id) {
        return bankAccountRepository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Account not found", "accountnotfound"));
    }

    private static void warnIfBalanceSupplied(BankAccountDTO dto, BigDecimal current) {
        if (dto.getBalance() != null && dto.getBalance().compareTo(current) != 0) {
            LOG.warn(
                "Ignored balance {} in admin CRUD for account id={} (balance is read-only, current {})",
                dto.getBalance(),
                dto.getId(),
                current
            );
        }
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateAccountNumber();
            if (!bankAccountRepository.existsByAccountNumber(candidate)) {
                // The unique constraint on account_number still protects against a concurrent insert of the same number
                return candidate;
            }
        }
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Could not allocate an account number, please retry",
            "accountnumberunavailable"
        );
    }

    static String generateAccountNumber() {
        int randomLength = ACCOUNT_NUMBER_LENGTH - ACCOUNT_NUMBER_PREFIX.length();
        StringBuilder sb = new StringBuilder(ACCOUNT_NUMBER_PREFIX);
        for (int i = 0; i < randomLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
