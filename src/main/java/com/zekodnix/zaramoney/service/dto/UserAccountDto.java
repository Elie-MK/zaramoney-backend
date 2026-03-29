package com.zekodnix.zaramoney.service.dto;

import jakarta.validation.constraints.Size;

public class UserAccountDto {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private String email;

    private String phoneNumber;

    private BankAccountDTO usdAccount;

    public UserAccountDto(String firstName, String lastName, String email, BankAccountDTO usdAccount, String phoneNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.usdAccount = usdAccount;
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
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

    public BankAccountDTO getUsdAccount() {
        return usdAccount;
    }

    public void setUsdAccount(BankAccountDTO usdAccount) {
        this.usdAccount = usdAccount;
    }
}
