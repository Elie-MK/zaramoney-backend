package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.LedgerEntryServicePlus;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.LedgerWithTransactionDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger-entries/plus")
public class LedgerEntryResourcePlus {

    private final LedgerEntryServicePlus ledgerEntryServicePlus;

    public LedgerEntryResourcePlus(LedgerEntryServicePlus ledgerEntryServicePlus) {
        this.ledgerEntryServicePlus = ledgerEntryServicePlus;
    }

    @GetMapping("/me")
    public ResponseEntity<Page<LedgerWithTransactionDTO>> getCurrentUserEntries(Pageable pageable) {
        var response = ledgerEntryServicePlus.getCurrentUserEntries(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transaction/{id}")
    public ResponseEntity<LedgerWithTransactionDTO> getLedgerEntry(@PathVariable Long id) {
        var response = ledgerEntryServicePlus.getCurrentUserEntryById(id);
        return ResponseEntity.ok(response);
    }
}
