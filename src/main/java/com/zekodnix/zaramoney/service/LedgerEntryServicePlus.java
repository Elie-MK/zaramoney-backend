package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEntryServicePlus {

    private final LedgerEntryService ledgerEntryService;

    public LedgerEntryServicePlus(LedgerEntryService ledgerEntryService) {
        this.ledgerEntryService = ledgerEntryService;
    }

    @Transactional
    public LedgerEntryDTO buildEntry(TransactionRecordDTO tx, BankAccountDTO account, BigDecimal sendAmount, EntryType type) {
        var entry = new LedgerEntryDTO();
        entry.setTransaction(tx);
        entry.setAccount(account);
        entry.setAmount(sendAmount);
        entry.setCurrency(Currency.USD);
        entry.setEntryType(type);
        return ledgerEntryService.save(entry);
    }

    public void validateDoubleEntry(LedgerEntryDTO debit, LedgerEntryDTO credit) {
        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException("Unbalanced transaction");
        }
    }
}
