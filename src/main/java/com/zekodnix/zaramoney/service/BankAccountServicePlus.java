package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    public BankAccountDTO createBankAccount(UserDTO user) {
        var bankAccountDTO = new BankAccountDTO();
        bankAccountDTO.setUser(user);
        bankAccountDTO.setBalance(BigDecimal.valueOf(5.00));
        bankAccountDTO.setAccountNumber(generateAccountNumber());
        bankAccountDTO.setCurrency(Currency.USD);
        bankAccountDTO.setStatus(AccountStatus.ACTIVE);

        return bankAccountService.save(bankAccountDTO);
    }

    private String generateAccountNumber() {
        String prefix = "2512";

        int remainingLength = 16 - prefix.length();

        long max = (long) Math.pow(10, remainingLength);
        long randomPart = (long) (Math.random() * max);

        String randomStr = String.format("%0" + remainingLength + "d", randomPart);

        return prefix + randomStr;
    }
}
