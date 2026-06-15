package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import jakarta.validation.constraints.NotNull;

public class LedgerWithTransactionDTO {

    @NotNull
    private EntryType entryType;

    private TransactionRecordDTO transactionRecord;

    public TransactionRecordDTO getTransactionRecord() {
        return transactionRecord;
    }

    public void setTransactionRecord(TransactionRecordDTO transactionRecord) {
        this.transactionRecord = transactionRecord;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }
}
