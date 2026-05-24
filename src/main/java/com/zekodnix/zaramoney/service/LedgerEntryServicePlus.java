package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEntryServicePlus {

    private final LedgerEntryService ledgerEntryService;

    public LedgerEntryServicePlus(LedgerEntryService ledgerEntryService) {
        this.ledgerEntryService = ledgerEntryService;
    }

    @Transactional
    public LedgerEntryDTO buildEntry(
        TransactionRecordDTO tx,
        BankAccountDTO account,
        TransactionRecordVM vm,
        EntryType type
    ) {
        var entry = new LedgerEntryDTO();
        entry.setTransaction(tx);
        entry.setAccount(account);
        entry.setAmount(vm.getSendAmount());
        entry.setCurrency(vm.getCurrencySendAmount());
        entry.setEntryType(type);
       return ledgerEntryService.save(entry);
    }

    public void validateDoubleEntry(LedgerEntryDTO debit, LedgerEntryDTO credit) {
        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException("Unbalanced transaction");
        }
    }



}
