package com.zekodnix.zaramoney.service.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class UserAccountDto {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private String email;

    private BankAccountDTO tndAccount;

    private BankAccountDTO usdAccount;

    public UserAccountDto(String firstName, String lastName, String email, BankAccountDTO tndAccount, BankAccountDTO usdAccount) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.tndAccount = tndAccount;
        this.usdAccount = usdAccount;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BankAccountDTO getTndAccount() {
        return tndAccount;
    }

    public void setTndAccount(BankAccountDTO tndAccount) {
        this.tndAccount = tndAccount;
    }

    public BankAccountDTO getUsdAccount() {
        return usdAccount;
    }

    public void setUsdAccount(BankAccountDTO usdAccount) {
        this.usdAccount = usdAccount;
    }
}
