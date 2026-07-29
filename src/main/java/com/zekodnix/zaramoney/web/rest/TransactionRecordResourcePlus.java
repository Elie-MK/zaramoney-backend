package com.zekodnix.zaramoney.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zekodnix.zaramoney.service.TransactionRecordServicePlus;
import com.zekodnix.zaramoney.service.dto.TransactionDetails;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import com.zekodnix.zaramoney.web.rest.vm.WithdrawalTransactionRecordVM;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plus/transaction-records")
public class TransactionRecordResourcePlus {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordResourcePlus.class);

    private final TransactionRecordServicePlus transactionRecordServicePlus;

    public TransactionRecordResourcePlus(TransactionRecordServicePlus transactionRecordServicePlus) {
        this.transactionRecordServicePlus = transactionRecordServicePlus;
    }

    @PostMapping("/send")
    public ResponseEntity<TransactionRecordDTO> sendTransactionRecord(@Valid @RequestBody TransactionRecordVM transactionRecordVM)
        throws AccessDeniedException, InterruptedException, JsonProcessingException {
        LOG.debug("REST request to send TransactionRecord : {}", transactionRecordVM);
        var response = transactionRecordServicePlus.createTransactionRecord(transactionRecordVM);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<TransactionRecordDTO> withdrawalTransaction(@Valid @RequestBody WithdrawalTransactionRecordVM vm)
        throws AccessDeniedException, JsonProcessingException {
        LOG.debug("REST request to withdrawal TransactionRecord : {}", vm);
        var response = transactionRecordServicePlus.withdrawalTransaction(vm);
        return ResponseEntity.ok(response);
    }

    @GetMapping("")
    public ResponseEntity<Page<TransactionRecordDTO>> getAllTransactionRecords(Pageable pageable) {
        LOG.debug("REST request to get TransactionRecords");
        var response = transactionRecordServicePlus.getAllTransactionRecords(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionRecordDTO> getTransactionRecord(@PathVariable Long id) {
        LOG.debug("REST request to get TransactionRecord : {}", id);
        var response = transactionRecordServicePlus.getTransactionRecord(id);
        return ResponseEntity.ok(response);
    }
}
