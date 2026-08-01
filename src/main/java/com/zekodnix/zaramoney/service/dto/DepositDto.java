package com.zekodnix.zaramoney.service.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

public class DepositDto {
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String userAccountNumber;
    @NotNull
    private String userEmail;

    public DepositDto() {
    }

    public DepositDto(BigDecimal amount, String userAccountNumber, String userEmail) {
        this.amount = amount;
        this.userAccountNumber = userAccountNumber;
        this.userEmail = userEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getUserAccountNumber() {
        return userAccountNumber;
    }

    public void setUserAccountNumber(String userAccountNumber) {
        this.userAccountNumber = userAccountNumber;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
