package com.zekodnix.zaramoney.web.rest.vm;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class TransactionRecordVM {

    @NotNull
    private TransactionType transactionType;

    @NotNull
    @DecimalMin(value = "1")
    private BigDecimal sendAmount;

    @Size(max = 255)
    private String description;

    @NotNull
    @Size(min = 8, max = 16)
    private String receiverAccountNumber;

    @NotNull
    private Currency currencySendAmount;

    @NotNull
    private Currency currencyReceiveAmount;

    @NotNull
    private String idempotencyKey;

    @NotNull
    private String endpoint;

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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
    }

    @Override
    public String toString() {
        return (
            "TransactionRecordVM{" +
            "transactionType=" +
            transactionType +
            ", sendAmount=" +
            sendAmount +
            ", description='" +
            description +
            '\'' +
            ", receiverAccountNumber='" +
            receiverAccountNumber +
            '\'' +
            ", currencySendAmount='" +
            currencySendAmount +
            '\'' +
            ", currencyReceiveAmount='" +
            currencyReceiveAmount +
            '\'' +
            ", idempotencyKey='" +
            idempotencyKey +
            '\'' +
            ", endpoint='" +
            endpoint +
            '\'' +
            '}'
        );
    }
}
