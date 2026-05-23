package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.zekodnix.zaramoney.domain.TransactionRecord} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRecordDTO implements Serializable {

    private Long id;

    @NotNull
    private TransactionType transactionType;

    @NotNull
    @DecimalMin(value = "1")
    private BigDecimal sendAmount;

    @NotNull
    @DecimalMin(value = "1")
    private BigDecimal receiveAmount;

    @NotNull
    private BigDecimal exchangeRate;

    @NotNull
    private Currency currencySendAmount;

    @NotNull
    private Currency currencyReceiveAmount;

    @NotNull
    private TransactionStatus transactionStatus;

    @NotNull
    @Size(min = 10, max = 64)
    private String transactionReference;

    @Size(max = 255)
    private String description;

    @NotNull
    @Min(value = 0)
    @Max(value = 100)
    private Integer riskScore;

    @NotNull
    private FraudStatus fraudStatus;

    @NotNull
    private Instant createdAt;

    private Instant updatedAt;

    @NotNull
    private Instant transactionDate;

    private BankAccountDTO sender;

    private BankAccountDTO receiver;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getSendAmount() {
        return sendAmount;
    }

    public void setSendAmount(BigDecimal sendAmount) {
        this.sendAmount = sendAmount;
    }

    public BigDecimal getReceiveAmount() {
        return receiveAmount;
    }

    public void setReceiveAmount(BigDecimal receiveAmount) {
        this.receiveAmount = receiveAmount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public Currency getCurrencySendAmount() {
        return currencySendAmount;
    }

    public void setCurrencySendAmount(Currency currencySendAmount) {
        this.currencySendAmount = currencySendAmount;
    }

    public Currency getCurrencyReceiveAmount() {
        return currencyReceiveAmount;
    }

    public void setCurrencyReceiveAmount(Currency currencyReceiveAmount) {
        this.currencyReceiveAmount = currencyReceiveAmount;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public FraudStatus getFraudStatus() {
        return fraudStatus;
    }

    public void setFraudStatus(FraudStatus fraudStatus) {
        this.fraudStatus = fraudStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BankAccountDTO getSender() {
        return sender;
    }

    public void setSender(BankAccountDTO sender) {
        this.sender = sender;
    }

    public BankAccountDTO getReceiver() {
        return receiver;
    }

    public void setReceiver(BankAccountDTO receiver) {
        this.receiver = receiver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRecordDTO)) {
            return false;
        }

        TransactionRecordDTO transactionRecordDTO = (TransactionRecordDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionRecordDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRecordDTO{" +
            "id=" + getId() +
            ", transactionType='" + getTransactionType() + "'" +
            ", sendAmount=" + getSendAmount() +
            ", receiveAmount=" + getReceiveAmount() +
            ", exchangeRate=" + getExchangeRate() +
            ", currencySendAmount='" + getCurrencySendAmount() + "'" +
            ", currencyReceiveAmount='" + getCurrencyReceiveAmount() + "'" +
            ", transactionStatus='" + getTransactionStatus() + "'" +
            ", transactionReference='" + getTransactionReference() + "'" +
            ", description='" + getDescription() + "'" +
            ", riskScore=" + getRiskScore() +
            ", fraudStatus='" + getFraudStatus() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", transactionDate='" + getTransactionDate() + "'" +
            ", sender=" + getSender() +
            ", receiver=" + getReceiver() +
            "}";
    }
}
