package com.zekodnix.zaramoney.service.criteria;

import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.BankAccount} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.BankAccountResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /bank-accounts?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BankAccountCriteria implements Serializable, Criteria {

    /**
     * Class for filtering Currency
     */
    public static class CurrencyFilter extends Filter<Currency> {

        public CurrencyFilter() {}

        public CurrencyFilter(CurrencyFilter filter) {
            super(filter);
        }

        @Override
        public CurrencyFilter copy() {
            return new CurrencyFilter(this);
        }
    }

    /**
     * Class for filtering AccountStatus
     */
    public static class AccountStatusFilter extends Filter<AccountStatus> {

        public AccountStatusFilter() {}

        public AccountStatusFilter(AccountStatusFilter filter) {
            super(filter);
        }

        @Override
        public AccountStatusFilter copy() {
            return new AccountStatusFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter accountNumber;

    private BigDecimalFilter balance;

    private CurrencyFilter currency;

    private AccountStatusFilter status;

    private InstantFilter createdAt;

    private LongFilter userId;

    private LongFilter transactionLimitId;

    private Boolean distinct;

    public BankAccountCriteria() {}

    public BankAccountCriteria(BankAccountCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.accountNumber = other.optionalAccountNumber().map(StringFilter::copy).orElse(null);
        this.balance = other.optionalBalance().map(BigDecimalFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(CurrencyFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(AccountStatusFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.transactionLimitId = other.optionalTransactionLimitId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public BankAccountCriteria copy() {
        return new BankAccountCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getAccountNumber() {
        return accountNumber;
    }

    public Optional<StringFilter> optionalAccountNumber() {
        return Optional.ofNullable(accountNumber);
    }

    public StringFilter accountNumber() {
        if (accountNumber == null) {
            setAccountNumber(new StringFilter());
        }
        return accountNumber;
    }

    public void setAccountNumber(StringFilter accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimalFilter getBalance() {
        return balance;
    }

    public Optional<BigDecimalFilter> optionalBalance() {
        return Optional.ofNullable(balance);
    }

    public BigDecimalFilter balance() {
        if (balance == null) {
            setBalance(new BigDecimalFilter());
        }
        return balance;
    }

    public void setBalance(BigDecimalFilter balance) {
        this.balance = balance;
    }

    public CurrencyFilter getCurrency() {
        return currency;
    }

    public Optional<CurrencyFilter> optionalCurrency() {
        return Optional.ofNullable(currency);
    }

    public CurrencyFilter currency() {
        if (currency == null) {
            setCurrency(new CurrencyFilter());
        }
        return currency;
    }

    public void setCurrency(CurrencyFilter currency) {
        this.currency = currency;
    }

    public AccountStatusFilter getStatus() {
        return status;
    }

    public Optional<AccountStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public AccountStatusFilter status() {
        if (status == null) {
            setStatus(new AccountStatusFilter());
        }
        return status;
    }

    public void setStatus(AccountStatusFilter status) {
        this.status = status;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public LongFilter getUserId() {
        return userId;
    }

    public Optional<LongFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public LongFilter userId() {
        if (userId == null) {
            setUserId(new LongFilter());
        }
        return userId;
    }

    public void setUserId(LongFilter userId) {
        this.userId = userId;
    }

    public LongFilter getTransactionLimitId() {
        return transactionLimitId;
    }

    public Optional<LongFilter> optionalTransactionLimitId() {
        return Optional.ofNullable(transactionLimitId);
    }

    public LongFilter transactionLimitId() {
        if (transactionLimitId == null) {
            setTransactionLimitId(new LongFilter());
        }
        return transactionLimitId;
    }

    public void setTransactionLimitId(LongFilter transactionLimitId) {
        this.transactionLimitId = transactionLimitId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BankAccountCriteria that = (BankAccountCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(accountNumber, that.accountNumber) &&
            Objects.equals(balance, that.balance) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(status, that.status) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(transactionLimitId, that.transactionLimitId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountNumber, balance, currency, status, createdAt, userId, transactionLimitId, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BankAccountCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalAccountNumber().map(f -> "accountNumber=" + f + ", ").orElse("") +
            optionalBalance().map(f -> "balance=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalTransactionLimitId().map(f -> "transactionLimitId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
