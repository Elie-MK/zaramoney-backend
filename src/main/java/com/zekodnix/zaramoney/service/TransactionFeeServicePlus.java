package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class TransactionFeeServicePlus {

    private final TransactionFeeService transactionFeeService;

    public TransactionFeeServicePlus(TransactionFeeService transactionFeeService) {
        this.transactionFeeService = transactionFeeService;
    }

    public void createTransactionFee(BigDecimal fee, TransactionRecordDTO result) {
        var transactionFee = new TransactionFeeDTO();
        transactionFee.setAmount(fee);
        transactionFee.setCurrency(Currency.USD);
        transactionFee.setTransaction(result);
        transactionFee.setType("transaction fee");
        transactionFeeService.save(transactionFee);
    }
}
