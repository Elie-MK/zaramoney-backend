package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.BankAccountServicePlus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.BankAccount}.
 */
@RestController
@RequestMapping("/api/bank-accounts/plus")
public class BankAccountResourcePlus {

    private static final Logger LOG = LoggerFactory.getLogger(BankAccountResourcePlus.class);

    private final BankAccountServicePlus bankAccountServicePlus;

    public BankAccountResourcePlus(BankAccountServicePlus bankAccountServicePlus) {
        this.bankAccountServicePlus = bankAccountServicePlus;
    }

    @GetMapping("/accounts/{accountNumber}/verify")
    public ResponseEntity<Boolean> verifyBankAccount(@PathVariable String accountNumber) {
        LOG.debug("REST request to verify account : {}", accountNumber);

        var res = bankAccountServicePlus.verifyAccount(accountNumber);

        return ResponseEntity.ok(res);
    }
}
