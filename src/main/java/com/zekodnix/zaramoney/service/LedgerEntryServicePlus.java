package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.LedgerWithTransactionDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEntryServicePlus {

    private final LedgerEntryService ledgerEntryService;
    private final TransactionRecordService transactionRecordService;

    public LedgerEntryServicePlus(LedgerEntryService ledgerEntryService, TransactionRecordService transactionRecordService) {
        this.ledgerEntryService = ledgerEntryService;
        this.transactionRecordService = transactionRecordService;
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

    @Transactional(readOnly = true)
    public Page<LedgerWithTransactionDTO> getCurrentUserEntries(Pageable pageable) {
        Page<LedgerEntryDTO> entries = ledgerEntryService.getCurrentUserEntries(pageable);

        return entries.map(e -> {
            LedgerWithTransactionDTO dto = new LedgerWithTransactionDTO();
            var transaction = transactionRecordService.findOne(e.getTransaction().getId()).orElseThrow();
            dto.setEntryType(e.getEntryType());
            dto.setTransactionRecord(transaction);
            return dto;
        });
    }

    public void validateDoubleEntry(LedgerEntryDTO debit, LedgerEntryDTO credit) {
        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException("Unbalanced transaction");
        }
    }

    public LedgerWithTransactionDTO getCurrentUserEntryById(Long id) {
        var transaction = transactionRecordService.findOne(id).orElseThrow();
        var ledger = ledgerEntryService.getCurrentUserEntryByTransactionId(id);
        return new LedgerWithTransactionDTO(ledger.getEntryType(), transaction);
    }
}
