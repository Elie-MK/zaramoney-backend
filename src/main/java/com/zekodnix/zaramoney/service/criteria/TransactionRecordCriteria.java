package com.zekodnix.zaramoney.service.criteria;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.TransactionRecord} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.TransactionRecordResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /transaction-records?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRecordCriteria implements Serializable, Criteria {

    /**
     * Class for filtering TransactionType
     */
    public static class TransactionTypeFilter extends Filter<TransactionType> {

        public TransactionTypeFilter() {}

        public TransactionTypeFilter(TransactionTypeFilter filter) {
            super(filter);
        }

        @Override
        public TransactionTypeFilter copy() {
            return new TransactionTypeFilter(this);
        }
    }

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
     * Class for filtering TransactionStatus
     */
    public static class TransactionStatusFilter extends Filter<TransactionStatus> {

        public TransactionStatusFilter() {}

        public TransactionStatusFilter(TransactionStatusFilter filter) {
            super(filter);
        }

        @Override
        public TransactionStatusFilter copy() {
            return new TransactionStatusFilter(this);
        }
    }

    /**
     * Class for filtering FraudStatus
     */
    public static class FraudStatusFilter extends Filter<FraudStatus> {

        public FraudStatusFilter() {}

        public FraudStatusFilter(FraudStatusFilter filter) {
            super(filter);
        }

        @Override
        public FraudStatusFilter copy() {
            return new FraudStatusFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private TransactionTypeFilter transactionType;

    private BigDecimalFilter sendAmount;

    private BigDecimalFilter receiveAmount;

    private BigDecimalFilter exchangeRate;

    private CurrencyFilter currencySendAmount;

    private CurrencyFilter currencyReceiveAmount;

    private TransactionStatusFilter transactionStatus;

    private StringFilter transactionReference;

    private StringFilter description;

    private IntegerFilter riskScore;

    private FraudStatusFilter fraudStatus;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private InstantFilter transactionDate;

    private LongFilter senderId;

    private LongFilter receiverId;

    private Boolean distinct;

    public TransactionRecordCriteria() {}

    public TransactionRecordCriteria(TransactionRecordCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.transactionType = other.optionalTransactionType().map(TransactionTypeFilter::copy).orElse(null);
        this.sendAmount = other.optionalSendAmount().map(BigDecimalFilter::copy).orElse(null);
        this.receiveAmount = other.optionalReceiveAmount().map(BigDecimalFilter::copy).orElse(null);
        this.exchangeRate = other.optionalExchangeRate().map(BigDecimalFilter::copy).orElse(null);
        this.currencySendAmount = other.optionalCurrencySendAmount().map(CurrencyFilter::copy).orElse(null);
        this.currencyReceiveAmount = other.optionalCurrencyReceiveAmount().map(CurrencyFilter::copy).orElse(null);
        this.transactionStatus = other.optionalTransactionStatus().map(TransactionStatusFilter::copy).orElse(null);
        this.transactionReference = other.optionalTransactionReference().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.riskScore = other.optionalRiskScore().map(IntegerFilter::copy).orElse(null);
        this.fraudStatus = other.optionalFraudStatus().map(FraudStatusFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.transactionDate = other.optionalTransactionDate().map(InstantFilter::copy).orElse(null);
        this.senderId = other.optionalSenderId().map(LongFilter::copy).orElse(null);
        this.receiverId = other.optionalReceiverId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public TransactionRecordCriteria copy() {
        return new TransactionRecordCriteria(this);
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

    public TransactionTypeFilter getTransactionType() {
        return transactionType;
    }

    public Optional<TransactionTypeFilter> optionalTransactionType() {
        return Optional.ofNullable(transactionType);
    }

    public TransactionTypeFilter transactionType() {
        if (transactionType == null) {
            setTransactionType(new TransactionTypeFilter());
        }
        return transactionType;
    }

    public void setTransactionType(TransactionTypeFilter transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimalFilter getSendAmount() {
        return sendAmount;
    }

    public Optional<BigDecimalFilter> optionalSendAmount() {
        return Optional.ofNullable(sendAmount);
    }

    public BigDecimalFilter sendAmount() {
        if (sendAmount == null) {
            setSendAmount(new BigDecimalFilter());
        }
        return sendAmount;
    }

    public void setSendAmount(BigDecimalFilter sendAmount) {
        this.sendAmount = sendAmount;
    }

    public BigDecimalFilter getReceiveAmount() {
        return receiveAmount;
    }

    public Optional<BigDecimalFilter> optionalReceiveAmount() {
        return Optional.ofNullable(receiveAmount);
    }

    public BigDecimalFilter receiveAmount() {
        if (receiveAmount == null) {
            setReceiveAmount(new BigDecimalFilter());
        }
        return receiveAmount;
    }

    public void setReceiveAmount(BigDecimalFilter receiveAmount) {
        this.receiveAmount = receiveAmount;
    }

    public BigDecimalFilter getExchangeRate() {
        return exchangeRate;
    }

    public Optional<BigDecimalFilter> optionalExchangeRate() {
        return Optional.ofNullable(exchangeRate);
    }

    public BigDecimalFilter exchangeRate() {
        if (exchangeRate == null) {
            setExchangeRate(new BigDecimalFilter());
        }
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimalFilter exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public CurrencyFilter getCurrencySendAmount() {
        return currencySendAmount;
    }

    public Optional<CurrencyFilter> optionalCurrencySendAmount() {
        return Optional.ofNullable(currencySendAmount);
    }

    public CurrencyFilter currencySendAmount() {
        if (currencySendAmount == null) {
            setCurrencySendAmount(new CurrencyFilter());
        }
        return currencySendAmount;
    }

    public void setCurrencySendAmount(CurrencyFilter currencySendAmount) {
        this.currencySendAmount = currencySendAmount;
    }

    public CurrencyFilter getCurrencyReceiveAmount() {
        return currencyReceiveAmount;
    }

    public Optional<CurrencyFilter> optionalCurrencyReceiveAmount() {
        return Optional.ofNullable(currencyReceiveAmount);
    }

    public CurrencyFilter currencyReceiveAmount() {
        if (currencyReceiveAmount == null) {
            setCurrencyReceiveAmount(new CurrencyFilter());
        }
        return currencyReceiveAmount;
    }

    public void setCurrencyReceiveAmount(CurrencyFilter currencyReceiveAmount) {
        this.currencyReceiveAmount = currencyReceiveAmount;
    }

    public TransactionStatusFilter getTransactionStatus() {
        return transactionStatus;
    }

    public Optional<TransactionStatusFilter> optionalTransactionStatus() {
        return Optional.ofNullable(transactionStatus);
    }

    public TransactionStatusFilter transactionStatus() {
        if (transactionStatus == null) {
            setTransactionStatus(new TransactionStatusFilter());
        }
        return transactionStatus;
    }

    public void setTransactionStatus(TransactionStatusFilter transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public StringFilter getTransactionReference() {
        return transactionReference;
    }

    public Optional<StringFilter> optionalTransactionReference() {
        return Optional.ofNullable(transactionReference);
    }

    public StringFilter transactionReference() {
        if (transactionReference == null) {
            setTransactionReference(new StringFilter());
        }
        return transactionReference;
    }

    public void setTransactionReference(StringFilter transactionReference) {
        this.transactionReference = transactionReference;
    }

    public StringFilter getDescription() {
        return description;
    }

    public Optional<StringFilter> optionalDescription() {
        return Optional.ofNullable(description);
    }

    public StringFilter description() {
        if (description == null) {
            setDescription(new StringFilter());
        }
        return description;
    }

    public void setDescription(StringFilter description) {
        this.description = description;
    }

    public IntegerFilter getRiskScore() {
        return riskScore;
    }

    public Optional<IntegerFilter> optionalRiskScore() {
        return Optional.ofNullable(riskScore);
    }

    public IntegerFilter riskScore() {
        if (riskScore == null) {
            setRiskScore(new IntegerFilter());
        }
        return riskScore;
    }

    public void setRiskScore(IntegerFilter riskScore) {
        this.riskScore = riskScore;
    }

    public FraudStatusFilter getFraudStatus() {
        return fraudStatus;
    }

    public Optional<FraudStatusFilter> optionalFraudStatus() {
        return Optional.ofNullable(fraudStatus);
    }

    public FraudStatusFilter fraudStatus() {
        if (fraudStatus == null) {
            setFraudStatus(new FraudStatusFilter());
        }
        return fraudStatus;
    }

    public void setFraudStatus(FraudStatusFilter fraudStatus) {
        this.fraudStatus = fraudStatus;
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

    public InstantFilter getUpdatedAt() {
        return updatedAt;
    }

    public Optional<InstantFilter> optionalUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public InstantFilter updatedAt() {
        if (updatedAt == null) {
            setUpdatedAt(new InstantFilter());
        }
        return updatedAt;
    }

    public void setUpdatedAt(InstantFilter updatedAt) {
        this.updatedAt = updatedAt;
    }

    public InstantFilter getTransactionDate() {
        return transactionDate;
    }

    public Optional<InstantFilter> optionalTransactionDate() {
        return Optional.ofNullable(transactionDate);
    }

    public InstantFilter transactionDate() {
        if (transactionDate == null) {
            setTransactionDate(new InstantFilter());
        }
        return transactionDate;
    }

    public void setTransactionDate(InstantFilter transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LongFilter getSenderId() {
        return senderId;
    }

    public Optional<LongFilter> optionalSenderId() {
        return Optional.ofNullable(senderId);
    }

    public LongFilter senderId() {
        if (senderId == null) {
            setSenderId(new LongFilter());
        }
        return senderId;
    }

    public void setSenderId(LongFilter senderId) {
        this.senderId = senderId;
    }

    public LongFilter getReceiverId() {
        return receiverId;
    }

    public Optional<LongFilter> optionalReceiverId() {
        return Optional.ofNullable(receiverId);
    }

    public LongFilter receiverId() {
        if (receiverId == null) {
            setReceiverId(new LongFilter());
        }
        return receiverId;
    }

    public void setReceiverId(LongFilter receiverId) {
        this.receiverId = receiverId;
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
        final TransactionRecordCriteria that = (TransactionRecordCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(transactionType, that.transactionType) &&
            Objects.equals(sendAmount, that.sendAmount) &&
            Objects.equals(receiveAmount, that.receiveAmount) &&
            Objects.equals(exchangeRate, that.exchangeRate) &&
            Objects.equals(currencySendAmount, that.currencySendAmount) &&
            Objects.equals(currencyReceiveAmount, that.currencyReceiveAmount) &&
            Objects.equals(transactionStatus, that.transactionStatus) &&
            Objects.equals(transactionReference, that.transactionReference) &&
            Objects.equals(description, that.description) &&
            Objects.equals(riskScore, that.riskScore) &&
            Objects.equals(fraudStatus, that.fraudStatus) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(transactionDate, that.transactionDate) &&
            Objects.equals(senderId, that.senderId) &&
            Objects.equals(receiverId, that.receiverId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            transactionType,
            sendAmount,
            receiveAmount,
            exchangeRate,
            currencySendAmount,
            currencyReceiveAmount,
            transactionStatus,
            transactionReference,
            description,
            riskScore,
            fraudStatus,
            createdAt,
            updatedAt,
            transactionDate,
            senderId,
            receiverId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRecordCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalTransactionType().map(f -> "transactionType=" + f + ", ").orElse("") +
            optionalSendAmount().map(f -> "sendAmount=" + f + ", ").orElse("") +
            optionalReceiveAmount().map(f -> "receiveAmount=" + f + ", ").orElse("") +
            optionalExchangeRate().map(f -> "exchangeRate=" + f + ", ").orElse("") +
            optionalCurrencySendAmount().map(f -> "currencySendAmount=" + f + ", ").orElse("") +
            optionalCurrencyReceiveAmount().map(f -> "currencyReceiveAmount=" + f + ", ").orElse("") +
            optionalTransactionStatus().map(f -> "transactionStatus=" + f + ", ").orElse("") +
            optionalTransactionReference().map(f -> "transactionReference=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalRiskScore().map(f -> "riskScore=" + f + ", ").orElse("") +
            optionalFraudStatus().map(f -> "fraudStatus=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalTransactionDate().map(f -> "transactionDate=" + f + ", ").orElse("") +
            optionalSenderId().map(f -> "senderId=" + f + ", ").orElse("") +
            optionalReceiverId().map(f -> "receiverId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
