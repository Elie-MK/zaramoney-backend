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
    private Instant transactionDate;

    @Size(max = 255)
    private String description;

    @NotNull
    @Size(min = 8, max = 16)
    private String senderAccountNumber;

    @NotNull
    @Size(min = 8, max = 16)
    private String receiverAccountNumber;

    @NotNull
    private Currency currencySendAmount;

    @NotNull
    private Currency currencyReceiveAmount;

    @NotNull
    private TransactionStatus transactionStatus;

    @NotNull
    @Size(min = 10, max = 64)
    private String transactionReference;

    @NotNull
    @Min(value = 0)
    @Max(value = 100)
    private Integer riskScore;

    @NotNull
    private FraudStatus fraudStatus;

    @NotNull
    private Instant createdAt;

    private Instant updatedAt;

    private UserDTO userLogin;

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

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSenderAccountNumber() {
        return senderAccountNumber;
    }

    public void setSenderAccountNumber(String senderAccountNumber) {
        this.senderAccountNumber = senderAccountNumber;
    }

    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
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

    public UserDTO getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(UserDTO userLogin) {
        this.userLogin = userLogin;
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
            ", transactionDate='" + getTransactionDate() + "'" +
            ", description='" + getDescription() + "'" +
            ", senderAccountNumber='" + getSenderAccountNumber() + "'" +
            ", receiverAccountNumber='" + getReceiverAccountNumber() + "'" +
            ", currencySendAmount='" + getCurrencySendAmount() + "'" +
            ", currencyReceiveAmount='" + getCurrencyReceiveAmount() + "'" +
            ", transactionStatus='" + getTransactionStatus() + "'" +
            ", transactionReference='" + getTransactionReference() + "'" +
            ", riskScore=" + getRiskScore() +
            ", fraudStatus='" + getFraudStatus() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", userLogin=" + getUserLogin() +
            "}";
    }
}
