package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import jakarta.validation.constraints.NotNull;

public class LedgerWithTransactionDTO {

    @NotNull
    private EntryType entryType;

    private TransactionReceiptDTO transactionRecord;

    public TransactionReceiptDTO getTransactionRecord() {
        return transactionRecord;
    }

    public void setTransactionRecord(TransactionReceiptDTO transactionRecord) {
        this.transactionRecord = transactionRecord;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public LedgerWithTransactionDTO(EntryType entryType, TransactionReceiptDTO transactionRecord) {
        this.entryType = entryType;
        this.transactionRecord = transactionRecord;
    }

    public LedgerWithTransactionDTO() {}
}
