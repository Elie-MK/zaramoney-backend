package com.zekodnix.zaramoney.service.criteria;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.TransactionLimit} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.TransactionLimitResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /transaction-limits?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionLimitCriteria implements Serializable, Criteria {

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private BigDecimalFilter dailyLimit;

    private BigDecimalFilter monthlyLimit;

    private LongFilter accountId;

    private Boolean distinct;

    public TransactionLimitCriteria() {}

    public TransactionLimitCriteria(TransactionLimitCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.dailyLimit = other.optionalDailyLimit().map(BigDecimalFilter::copy).orElse(null);
        this.monthlyLimit = other.optionalMonthlyLimit().map(BigDecimalFilter::copy).orElse(null);
        this.accountId = other.optionalAccountId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public TransactionLimitCriteria copy() {
        return new TransactionLimitCriteria(this);
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

    public BigDecimalFilter getDailyLimit() {
        return dailyLimit;
    }

    public Optional<BigDecimalFilter> optionalDailyLimit() {
        return Optional.ofNullable(dailyLimit);
    }

    public BigDecimalFilter dailyLimit() {
        if (dailyLimit == null) {
            setDailyLimit(new BigDecimalFilter());
        }
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimalFilter dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimalFilter getMonthlyLimit() {
        return monthlyLimit;
    }

    public Optional<BigDecimalFilter> optionalMonthlyLimit() {
        return Optional.ofNullable(monthlyLimit);
    }

    public BigDecimalFilter monthlyLimit() {
        if (monthlyLimit == null) {
            setMonthlyLimit(new BigDecimalFilter());
        }
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimalFilter monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
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
        final TransactionLimitCriteria that = (TransactionLimitCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(dailyLimit, that.dailyLimit) &&
            Objects.equals(monthlyLimit, that.monthlyLimit) &&
            Objects.equals(accountId, that.accountId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dailyLimit, monthlyLimit, accountId, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionLimitCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalDailyLimit().map(f -> "dailyLimit=" + f + ", ").orElse("") +
            optionalMonthlyLimit().map(f -> "monthlyLimit=" + f + ", ").orElse("") +
            optionalAccountId().map(f -> "accountId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
