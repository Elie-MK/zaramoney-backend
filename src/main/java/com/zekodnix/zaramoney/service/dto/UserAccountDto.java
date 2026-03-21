package com.zekodnix.zaramoney.service.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class UserAccountDto {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private String accountNumber;

    private BigDecimal accountBalance;

    private String email;

    private String phoneNumber;

    public UserAccountDto(String firstName, String lastName, String accountNumber, BigDecimal accountBalance, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.accountNumber = accountNumber;
        this.accountBalance = accountBalance;
        this.email = email;
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
