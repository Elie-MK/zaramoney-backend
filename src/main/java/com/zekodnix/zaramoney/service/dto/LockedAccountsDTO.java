package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.BankAccount;

public class LockedAccountsDTO {

    public BankAccount senderAccount;
    public BankAccount receiverAccount;

    public LockedAccountsDTO(BankAccount senderAccount, BankAccount receiverAccount) {
        this.senderAccount = senderAccount;
        this.receiverAccount = receiverAccount;
    }

    public BankAccount getSenderAccount() {
        return senderAccount;
    }

    public void setSenderAccount(BankAccount senderAccount) {
        this.senderAccount = senderAccount;
    }

    public BankAccount getReceiverAccount() {
        return receiverAccount;
    }

    public void setReceiverAccount(BankAccount receiverAccount) {
        this.receiverAccount = receiverAccount;
    }
}
