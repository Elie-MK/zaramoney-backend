package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransactionFeeServicePlus {
    private final TransactionFeeService transactionFeeService;

    public TransactionFeeServicePlus(TransactionFeeService transactionFeeService) {
        this.transactionFeeService = transactionFeeService;
    }

    public void createTransactionFee(TransactionRecordVM vm, BigDecimal fee, TransactionRecordDTO result) {
        var transactionFee = new TransactionFeeDTO();
        transactionFee.setAmount(fee);
        transactionFee.setCurrency(vm.getCurrencySendAmount());
        transactionFee.setTransaction(result);
        transactionFee.setType("transaction fee");
        transactionFeeService.save(transactionFee);
    }
}
