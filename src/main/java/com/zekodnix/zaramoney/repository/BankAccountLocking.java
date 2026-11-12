package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.BankAccount;
import java.util.Optional;

/**
 * Row locks on bank accounts that always return the CURRENT balance (Spring Data fragment of
 * {@link BankAccountRepository}, implemented in {@link BankAccountLockingImpl}).
 *
 * <p>Why not a plain {@code @Lock(PESSIMISTIC_WRITE)} query: when the account entity is already in the persistence
 * context (e.g. the counterparty was looked up earlier in the same transaction), Hibernate takes the row lock but
 * keeps the STALE in-memory state and ignores the fresh row. A balance computed from it then overwrites concurrent
 * transfers (lost update). These methods lock, then re-read the row from the database under the lock.
 */
public interface BankAccountLocking {
    /** SELECT … FOR UPDATE by account number, then refresh: the returned entity holds the latest committed balance. */
    Optional<BankAccount> findByAccountNumberForUpdate(String accountNumber);

    /** Same, by id. */
    Optional<BankAccount> findByIdForUpdate(Long id);
}
