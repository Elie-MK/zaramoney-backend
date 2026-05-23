package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionLimitServicePlus {

    private final TransactionLimitService transactionLimitService;

    public TransactionLimitServicePlus(TransactionLimitService transactionLimitService) {
        this.transactionLimitService = transactionLimitService;
    }

    public void createBankAccountLimit(BankAccountDTO account) {
        var transactionLimit = new TransactionLimitDTO();
        transactionLimit.setDailyLimit(BigDecimal.valueOf(1000));
        transactionLimit.setMonthlyLimit(BigDecimal.valueOf(10000));
        transactionLimit.setAccount(account);
        transactionLimitService.save(transactionLimit);
    }
}
