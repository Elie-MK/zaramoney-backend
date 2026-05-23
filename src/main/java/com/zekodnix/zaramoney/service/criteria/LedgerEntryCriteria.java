package com.zekodnix.zaramoney.service.criteria;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.LedgerEntry} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.LedgerEntryResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /ledger-entries?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerEntryCriteria implements Serializable, Criteria {

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
     * Class for filtering EntryType
     */
    public static class EntryTypeFilter extends Filter<EntryType> {

        public EntryTypeFilter() {}

        public EntryTypeFilter(EntryTypeFilter filter) {
            super(filter);
        }

        @Override
        public EntryTypeFilter copy() {
            return new EntryTypeFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private BigDecimalFilter amount;

    private CurrencyFilter currency;

    private EntryTypeFilter entryType;

    private InstantFilter createdAt;

    private LongFilter accountId;

    private LongFilter transactionId;

    private Boolean distinct;

    public LedgerEntryCriteria() {}

    public LedgerEntryCriteria(LedgerEntryCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.amount = other.optionalAmount().map(BigDecimalFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(CurrencyFilter::copy).orElse(null);
        this.entryType = other.optionalEntryType().map(EntryTypeFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.accountId = other.optionalAccountId().map(LongFilter::copy).orElse(null);
        this.transactionId = other.optionalTransactionId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public LedgerEntryCriteria copy() {
        return new LedgerEntryCriteria(this);
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

    public BigDecimalFilter getAmount() {
        return amount;
    }

    public Optional<BigDecimalFilter> optionalAmount() {
        return Optional.ofNullable(amount);
    }

    public BigDecimalFilter amount() {
        if (amount == null) {
            setAmount(new BigDecimalFilter());
        }
        return amount;
    }

    public void setAmount(BigDecimalFilter amount) {
        this.amount = amount;
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

    public EntryTypeFilter getEntryType() {
        return entryType;
    }

    public Optional<EntryTypeFilter> optionalEntryType() {
        return Optional.ofNullable(entryType);
    }

    public EntryTypeFilter entryType() {
        if (entryType == null) {
            setEntryType(new EntryTypeFilter());
        }
        return entryType;
    }

    public void setEntryType(EntryTypeFilter entryType) {
        this.entryType = entryType;
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

    public LongFilter getAccountId() {
        return accountId;
    }

    public Optional<LongFilter> optionalAccountId() {
        return Optional.ofNullable(accountId);
    }

    public LongFilter accountId() {
        if (accountId == null) {
            setAccountId(new LongFilter());
        }
        return accountId;
    }

    public void setAccountId(LongFilter accountId) {
        this.accountId = accountId;
    }

    public LongFilter getTransactionId() {
        return transactionId;
    }

    public Optional<LongFilter> optionalTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    public LongFilter transactionId() {
        if (transactionId == null) {
            setTransactionId(new LongFilter());
        }
        return transactionId;
    }

    public void setTransactionId(LongFilter transactionId) {
        this.transactionId = transactionId;
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
        final LedgerEntryCriteria that = (LedgerEntryCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(amount, that.amount) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(entryType, that.entryType) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(accountId, that.accountId) &&
            Objects.equals(transactionId, that.transactionId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount, currency, entryType, createdAt, accountId, transactionId, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LedgerEntryCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalAmount().map(f -> "amount=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalEntryType().map(f -> "entryType=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalAccountId().map(f -> "accountId=" + f + ", ").orElse("") +
            optionalTransactionId().map(f -> "transactionId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
