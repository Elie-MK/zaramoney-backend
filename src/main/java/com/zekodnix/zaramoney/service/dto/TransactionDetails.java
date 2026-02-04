package com.zekodnix.zaramoney.service.dto;

import java.math.BigDecimal;

public class TransactionDetails {

    private BigDecimal sendAmount;
    private String receiverAccountNumber;
    private String receiverName;
    private BigDecimal receiveAmount;

    public BigDecimal getSendAmount() {
        return sendAmount;
    }

    public void setSendAmount(BigDecimal sendAmount) {
        this.sendAmount = sendAmount;
    }

    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public BigDecimal getReceiveAmount() {
        return receiveAmount;
    }

    public void setReceiveAmount(BigDecimal receiveAmount) {
        this.receiveAmount = receiveAmount;
    }

    @Override
    public String toString() {
        return (
            "TransactionDetails{" +
            "sendAmount=" +
            sendAmount +
            ", receiverAccountNumber='" +
            receiverAccountNumber +
            '\'' +
            ", receiverName='" +
            receiverName +
            '\'' +
            ", receiveAmount=" +
            receiveAmount +
            '}'
        );
    }
}
