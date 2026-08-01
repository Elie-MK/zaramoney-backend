package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.BackOfficeService;
import com.zekodnix.zaramoney.service.dto.DepositDto;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backoffice")
public class BackOfficeResource {

    Logger Log = LoggerFactory.getLogger(BackOfficeResource.class);

    private final BackOfficeService backOfficeService;

    public BackOfficeResource(BackOfficeService backOfficeService) {
        this.backOfficeService = backOfficeService;
    }

    @PostMapping("/user/deposit")
    public ResponseEntity<TransactionRecordDTO> moneyDeposit(@RequestBody @Valid DepositDto depositDto ) {
        Log.debug("Make deposit to an user amount {}", depositDto.getAmount());

        var res = backOfficeService.addMoneyToUserAccount(depositDto);
        return ResponseEntity.ok(res);
    }
}
