package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import org.springframework.stereotype.Service;

@Service
public class BankAccountServicePlus {

    private final BankAccountService bankAccountService;

    public BankAccountServicePlus(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    public LockedAccountsDTO lockAccounts(String senderAcc, String receiverAcc) {
        // Always lock in consistent order (important for deadlocks)
        if (senderAcc.compareTo(receiverAcc) < 0) {
            return new LockedAccountsDTO(
                bankAccountService.findByAccountNumberForUpdate(senderAcc).orElseThrow(),
                bankAccountService.findByAccountNumberForUpdate(receiverAcc).orElseThrow()
            );
        } else {
            var receiver = bankAccountService.findByAccountNumberForUpdate(receiverAcc).orElseThrow();
            var sender = bankAccountService.findByAccountNumberForUpdate(senderAcc).orElseThrow();
            return new LockedAccountsDTO(sender, receiver);
        }
    }

    public Boolean verifyAccount(String accountNumber) {
        return bankAccountService.findByAccountNumber(accountNumber).isPresent();
    }
}
