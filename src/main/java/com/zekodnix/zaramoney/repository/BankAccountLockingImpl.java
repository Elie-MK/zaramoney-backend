package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.BankAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

/** See {@link BankAccountLocking}. Must run inside a transaction (the lock is held until it ends). */
class BankAccountLockingImpl implements BankAccountLocking {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<BankAccount> findByAccountNumberForUpdate(String accountNumber) {
        return em
            .createQuery("select b from BankAccount b where b.accountNumber = :accountNumber", BankAccount.class)
            .setParameter("accountNumber", accountNumber)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream()
            .findFirst()
            .map(this::reloadUnderLock);
    }

    @Override
    public Optional<BankAccount> findByIdForUpdate(Long id) {
        return em
            .createQuery("select b from BankAccount b where b.id = :id", BankAccount.class)
            .setParameter("id", id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream()
            .findFirst()
            .map(this::reloadUnderLock);
    }

    /**
     * The query above holds the row lock; if the entity was already managed, its state may be stale. Re-read it from
     * the database (never from the second-level cache) while we hold the lock.
     */
    private BankAccount reloadUnderLock(BankAccount account) {
        em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        return account;
    }
}
