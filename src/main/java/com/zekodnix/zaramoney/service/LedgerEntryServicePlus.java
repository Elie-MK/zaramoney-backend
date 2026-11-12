package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.LedgerWithTransactionDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEntryServicePlus {

    private final LedgerEntryService ledgerEntryService;
    private final TransactionReceiptService transactionReceiptService;

    public LedgerEntryServicePlus(LedgerEntryService ledgerEntryService, TransactionReceiptService transactionReceiptService) {
        this.ledgerEntryService = ledgerEntryService;
        this.transactionReceiptService = transactionReceiptService;
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
        // The ledger query is already scoped to the current user's accounts; receipts never expose the other side's data
        return entries.map(e ->
            new LedgerWithTransactionDTO(e.getEntryType(), transactionReceiptService.findOneForCurrentUser(e.getTransaction().getId()))
        );
    }

    public void validateDoubleEntry(LedgerEntryDTO debit, LedgerEntryDTO credit) {
        validateDoubleEntry(java.util.List.of(debit, credit));
    }

    /** All entries of one transaction: sum of DEBITs must equal sum of CREDITs (and there must be at least one of each). */
    public void validateDoubleEntry(java.util.List<LedgerEntryDTO> entries) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntryDTO e : entries) {
            if (e.getAmount() == null || e.getAmount().signum() < 0) {
                throw new IllegalStateException("Ledger entry with a missing or negative amount");
            }
            if (e.getEntryType() == EntryType.DEBIT) {
                debits = debits.add(e.getAmount());
            } else {
                credits = credits.add(e.getAmount());
            }
        }
        if (debits.signum() == 0 || debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Unbalanced transaction: debits " + debits + " != credits " + credits);
        }
    }

    @Transactional(readOnly = true)
    public LedgerWithTransactionDTO getCurrentUserEntryById(Long transactionId) {
        var ledger = ledgerEntryService.getCurrentUserEntryByTransactionId(transactionId);
        if (ledger == null) {
            throw ApiException.notFound("Transaction not found", "transactionnotfound");
        }
        return new LedgerWithTransactionDTO(ledger.getEntryType(), transactionReceiptService.findOneForCurrentUser(transactionId));
    }
}
