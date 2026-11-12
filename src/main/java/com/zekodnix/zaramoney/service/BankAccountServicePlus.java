package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankAccountServicePlus {

    private static final String ACCOUNT_NUMBER_PREFIX = "2512";
    private static final int ACCOUNT_NUMBER_LENGTH = 12;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankAccountService bankAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionLimitServicePlus transactionLimitServicePlus;
    private final SignupBonusService signupBonusService;

    public BankAccountServicePlus(
        BankAccountService bankAccountService,
        BankAccountRepository bankAccountRepository,
        TransactionLimitServicePlus transactionLimitServicePlus,
        SignupBonusService signupBonusService
    ) {
        this.bankAccountService = bankAccountService;
        this.bankAccountRepository = bankAccountRepository;
        this.transactionLimitServicePlus = transactionLimitServicePlus;
        this.signupBonusService = signupBonusService;
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
