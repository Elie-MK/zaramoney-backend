package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.TransactionRecordServicePlus;
import com.zekodnix.zaramoney.service.dto.TransactionReceiptDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordVM;
import com.zekodnix.zaramoney.service.dto.WithdrawalTransactionRecordVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Customer money movement. Responses are {@link TransactionReceiptDTO}s: never another user's balance or email. */
@RestController
@RequestMapping("/api/plus/transaction-records")
public class TransactionRecordResourcePlus {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordResourcePlus.class);

    private final TransactionRecordServicePlus transactionRecordServicePlus;

    public TransactionRecordResourcePlus(TransactionRecordServicePlus transactionRecordServicePlus) {
        this.transactionRecordServicePlus = transactionRecordServicePlus;
    }

    @PostMapping("/send")
    public ResponseEntity<TransactionReceiptDTO> sendTransactionRecord(@Valid @RequestBody TransactionRecordVM transactionRecordVM) {
        LOG.debug("REST request to send money, amount {}", transactionRecordVM.getSendAmount()); // never log the PIN
        return ResponseEntity.ok(transactionRecordServicePlus.createTransactionRecord(transactionRecordVM));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<TransactionReceiptDTO> withdrawalTransaction(@Valid @RequestBody WithdrawalTransactionRecordVM vm) {
        LOG.debug("REST request to withdraw, amount {}", vm.getSendAmount());
        return ResponseEntity.ok(transactionRecordServicePlus.withdrawalTransaction(vm));
    }

    /** The current user's transactions only. */
    @GetMapping("")
    public ResponseEntity<Page<TransactionReceiptDTO>> getAllTransactionRecords(Pageable pageable) {
        return ResponseEntity.ok(transactionRecordServicePlus.getAllTransactionRecords(pageable));
    }

    /** 404 if the transaction does not belong to the current user. */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionReceiptDTO> getTransactionRecord(@PathVariable Long id) {
        return ResponseEntity.ok(transactionRecordServicePlus.getTransactionRecord(id));
    }
}
