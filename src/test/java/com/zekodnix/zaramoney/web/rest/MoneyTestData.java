package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.BankAccountType;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.TransactionPinService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Committed test customers for money-flow ITs that are not @Transactional (PIN / REQUIRES_NEW / after-commit code).
 * Call {@link #cleanUp()} from {@code @AfterEach}: deletes run inside a transaction (the test pool is auto-commit=false).
 */
class MoneyTestData {

    static final String PASSWORD = "Str0ng-Passw0rd";
    static final String PIN = "4826";

    /**
     * ledger_entry is append-only (DB trigger). Test clean-up runs this first, inside the same transaction, to be
     * allowed to delete its own entries ({@code SET LOCAL}: ends with the transaction).
     */
    static final String LEDGER_MAINTENANCE = "set local zaramoney.ledger_maintenance = 'on'";

    record Customer(User user, BankAccount account) {
        String login() {
            return user.getLogin();
        }

        String accountNumber() {
            return account.getAccountNumber();
        }
    }

    /** What to create; defaults: ACTIVE USD account, KYC VERIFIED, not an agent, PIN set, no push token. */
    static final class Spec {

        String balance = "100.00";
        AccountStatus status = AccountStatus.ACTIVE;
        Currency currency = Currency.USD;
        boolean agent;
        boolean pin = true;
        String pushToken;
        KycStatus kyc = KycStatus.VERIFIED;

        Spec kyc(KycStatus v) {
            kyc = v;
            return this;
        }

        Spec balance(String v) {
            balance = v;
            return this;
        }

        Spec status(AccountStatus v) {
            status = v;
            return this;
        }

        Spec currency(Currency v) {
            currency = v;
            return this;
        }

        Spec agent() {
            agent = true;
            return this;
        }

        Spec pushToken(String v) {
            pushToken = v;
            return this;
        }
    }

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserDetailsAccountRepository userDetailsAccountRepository;
    private final TransactionPinService transactionPinService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate tx;
    private final List<Long> createdUserIds = new ArrayList<>();

    MoneyTestData(
        UserRepository userRepository,
        BankAccountRepository bankAccountRepository,
        UserDetailsAccountRepository userDetailsAccountRepository,
        TransactionPinService transactionPinService,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.transactionPinService = transactionPinService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.tx = new TransactionTemplate(transactionManager);
    }

    static Spec spec() {
        return new Spec();
    }

    Customer customer(Spec spec) {
        Customer created = tx.execute(s -> {
            String login = "it-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
            User user = new User();
            user.setLogin(login);
            user.setEmail(login);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setActivated(true);
            user.setFirstName("First");
            user.setLastName("Last");
            user.setLangKey("en");
            user = userRepository.saveAndFlush(user);
            BankAccount account = bankAccountRepository.saveAndFlush(
                new BankAccount()
                    .accountNumber("2512" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)))
                    .balance(new BigDecimal(spec.balance))
                    .currency(spec.currency)
                    .status(spec.status)
                    .accountType(BankAccountType.CUSTOMER)
                    .createdAt(Instant.now())
                    .user(user)
            );
            userDetailsAccountRepository.saveAndFlush(
                new UserDetailsAccount()
                    .phoneNumber("+243000000000")
                    .facePicture("kyc/face.png")
                    .idCardPicture("kyc/id.png")
                    .country("CD")
                    .address("Kinshasa")
                    .isAgent(spec.agent)
                    .kycStatus(spec.kyc)
                    .expoPushToken(spec.pushToken)
                    .user(user)
            );
            return new Customer(user, account);
        });
        createdUserIds.add(created.user().getId());
        if (spec.pin) {
            transactionPinService.setPin(created.user(), PASSWORD, PIN);
        }
        return created;
    }

    /** A logged-in user with no wallet and no KYC record. */
    User userWithoutAccount() {
        User created = tx.execute(s -> {
            String login = "it-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
            User user = new User();
            user.setLogin(login);
            user.setEmail(login);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setActivated(true);
            user.setLangKey("en");
            return userRepository.saveAndFlush(user);
        });
        createdUserIds.add(created.getId());
        return created;
    }

    BigDecimal balance(Customer c) {
        return bankAccountRepository.findById(c.account().getId()).orElseThrow().getBalance();
    }

    BigDecimal systemBalance(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    void cleanUp() {
        tx.executeWithoutResult(status -> createdUserIds.forEach(this::deleteUserData));
        createdUserIds.clear();
    }

    private void deleteUserData(Long userId) {
        String accounts = "(select id from bank_account where user_id = " + userId + ")";
        String txs = "(select id from transaction_record where sender_id in " + accounts + " or receiver_id in " + accounts + ")";
        jdbcTemplate.update("delete from notification where user_id = ? or transaction_id in " + txs, userId);
        jdbcTemplate.execute(MoneyTestData.LEDGER_MAINTENANCE);
        jdbcTemplate.update("delete from ledger_entry where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_fee where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_record where id in " + txs);
        jdbcTemplate.update("delete from transaction_limit where account_id in " + accounts);
        jdbcTemplate.update("delete from idempotency_record where user_id = ?", userId);
        jdbcTemplate.update("delete from transaction_pin where user_id = ?", userId);
        jdbcTemplate.update("delete from user_details_account where user_id = ?", userId);
        jdbcTemplate.update("delete from bank_account where user_id = ?", userId);
        jdbcTemplate.update("delete from refresh_token where session_id in (select id from user_session where user_id = ?)", userId);
        jdbcTemplate.update("delete from user_session where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user where id = ?", userId);
    }
}
