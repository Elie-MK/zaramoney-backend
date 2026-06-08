package com.zekodnix.zaramoney.web.rest.vm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class WithdrawalTransactionRecordVM {

    @NotNull
    @Size(min = 8, max = 16)
    private String agentAccountNumber;

    @NotNull
    @DecimalMin(value = "1")
    private BigDecimal sendAmount;

    @NotNull
    private String idempotencyKey;

    @NotNull
    private String password;

    public String getAgentAccountNumber() {
        return agentAccountNumber;
    }

    public void setAgentAccountNumber(String agentAccountNumber) {
        this.agentAccountNumber = agentAccountNumber;
    }

    public BigDecimal getSendAmount() {
        return sendAmount;
    }

    public void setSendAmount(BigDecimal sendAmount) {
        this.sendAmount = sendAmount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
