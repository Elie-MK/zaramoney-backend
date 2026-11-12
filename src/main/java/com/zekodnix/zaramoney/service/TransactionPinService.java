package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.domain.TransactionPin;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.TransactionPinRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.security.device.RevokeUserSessionsEvent;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction PIN (4 to 6 digits, BCrypt-hashed), separate from the login password.
 * After {@link #MAX_FAILED_ATTEMPTS} wrong PINs, the PIN is locked for {@link #LOCK_DURATION}.
 */
@Service
public class TransactionPinService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4,6}");

    private final TransactionPinRepository transactionPinRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    /** Runs the PIN check in its own transaction: the failed-attempt counter must be saved even if the transfer rolls back. */
    private final TransactionTemplate requiresNew;
    private Clock clock = Clock.systemUTC();

    private final AuditService auditService;

    public TransactionPinService(
        TransactionPinRepository transactionPinRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        PlatformTransactionManager transactionManager,
        ApplicationEventPublisher eventPublisher,
        AuditService auditService
    ) {
        this.auditService = auditService;
        this.transactionPinRepository = transactionPinRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** For tests. */
    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isPinSet(Long userId) {
        return transactionPinRepository.existsByUserId(userId);
    }

    /**
     * Sets or changes the PIN. Requires the account (login) password, so a stolen unlocked phone or a stolen token
     * alone cannot change it. Resets the failed-attempt counter and any lock.
     */
    @Transactional
    public void setPin(User user, String currentPassword, String newPin) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw ApiException.badRequest("Invalid password", "invalidpassword");
        }
        validatePinStrength(newPin);

        TransactionPin pin = transactionPinRepository.findOneByUserIdForUpdate(user.getId()).orElseGet(TransactionPin::new);
        if (pin.getId() == null) {
            pin.setUser(userRepository.getReferenceById(user.getId()));
        }
        pin.setPinHash(passwordEncoder.encode(newPin));
        pin.setFailedAttempts(0);
        pin.setLockedUntil(null);
        boolean firstPin = pin.getId() == null;
        pin.setUpdatedAt(Instant.now(clock));
        transactionPinRepository.save(pin);
        auditService.record(AuditAction.PIN_SET, "USER", user.getLogin(), firstPin ? "first PIN" : "PIN changed, other sessions revoked");
        // PIN changed: log out the other devices (keep the one that changed it)
        eventPublisher.publishEvent(new RevokeUserSessionsEvent(user.getId(), SecurityUtils.getCurrentSessionId().orElse(null), "pin_changed"));
    }

    /**
     * Verifies the PIN or throws: 400 {@code pinnotset} / {@code invalidpin}, 423 {@code pinlocked}.
     * Safe to call inside the transfer transaction.
     */
    public void verifyPin(Long userId, String rawPin) {
        PinCheck result = requiresNew.execute(status -> check(userId, rawPin));
        switch (result.outcome()) {
            case OK -> {
                return;
            }
            case NOT_SET -> throw ApiException.badRequest("Transaction PIN not set", "pinnotset");
            case LOCKED -> throw locked(result.lockedUntil());
            case INVALID -> {
                if (result.lockedUntil() != null) {
                    throw locked(result.lockedUntil());
                }
                throw ApiException.badRequest(
                    "Invalid PIN, " + result.remainingAttempts() + " attempt(s) left before the PIN is locked",
                    "invalidpin"
                );
            }
        }
    }

    private PinCheck check(Long userId, String rawPin) {
        var maybePin = transactionPinRepository.findOneByUserIdForUpdate(userId);
        if (maybePin.isEmpty()) {
            return new PinCheck(Outcome.NOT_SET, 0, null);
        }
        TransactionPin pin = maybePin.orElseThrow();
        Instant now = Instant.now(clock);

        if (pin.getLockedUntil() != null && pin.getLockedUntil().isAfter(now)) {
            return new PinCheck(Outcome.LOCKED, 0, pin.getLockedUntil());
        }

        if (rawPin != null && passwordEncoder.matches(rawPin, pin.getPinHash())) {
            if (pin.getFailedAttempts() != 0 || pin.getLockedUntil() != null) {
                pin.setFailedAttempts(0);
                pin.setLockedUntil(null);
                transactionPinRepository.save(pin);
            }
            return new PinCheck(Outcome.OK, MAX_FAILED_ATTEMPTS, null);
        }

        int failed = pin.getFailedAttempts() + 1;
        Instant lockedUntil = null;
        if (failed >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failed = 0;
            // Someone may be guessing the PIN with a stolen session: log out every device (committed with the counter)
            eventPublisher.publishEvent(new RevokeUserSessionsEvent(userId, null, "pin_locked"));
        }
        pin.setFailedAttempts(failed);
        pin.setLockedUntil(lockedUntil);
        transactionPinRepository.save(pin);
        return new PinCheck(Outcome.INVALID, MAX_FAILED_ATTEMPTS - failed, lockedUntil);
    }

    static void validatePinStrength(String pin) {
        if (pin == null || !PIN_FORMAT.matcher(pin).matches()) {
            throw ApiException.badRequest("The PIN must be 4 to 6 digits", "invalidpinformat");
        }
        if (pin.chars().distinct().count() == 1 || isSequence(pin, 1) || isSequence(pin, -1)) {
            throw ApiException.badRequest("This PIN is too easy to guess (e.g. 1111, 1234, 4321)", "weakpin");
        }
    }

    private static boolean isSequence(String pin, int step) {
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) - pin.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }

    private ApiException locked(Instant lockedUntil) {
        Duration retry = Duration.between(Instant.now(clock), lockedUntil);
        ApiException ex = new ApiException(
            HttpStatus.LOCKED,
            "Too many wrong PINs, the PIN is locked until " + lockedUntil,
            "pinlocked"
        );
        ex.getHeaders().set("Retry-After", String.valueOf(Math.max(1, retry.toSeconds())));
        return ex;
    }

    private enum Outcome {
        OK,
        NOT_SET,
        LOCKED,
        INVALID,
    }

    private record PinCheck(Outcome outcome, int remainingAttempts, Instant lockedUntil) {}
}
