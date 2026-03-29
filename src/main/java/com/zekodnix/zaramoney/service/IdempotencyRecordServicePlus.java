package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.IdempotencyRecordMapper;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import com.zekodnix.zaramoney.web.rest.vm.TransactionRecordVM;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyRecordServicePlus {

    private final IdempotencyRecordService idempotencyRecordService;
    private final TransactionRecordMapper transactionRecordMapper;
    private final IdempotencyRecordMapper idempotencyRecordMapper;

    public IdempotencyRecordServicePlus(
        IdempotencyRecordService idempotencyRecordService,
        TransactionRecordMapper transactionRecordMapper,
        IdempotencyRecordMapper idempotencyRecordMapper
    ) {
        this.idempotencyRecordService = idempotencyRecordService;
        this.transactionRecordMapper = transactionRecordMapper;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
    }

    public TransactionRecordDTO check(String key) {
        var hash = hashIdempotencyKey(key);
        return idempotencyRecordService.findByKeyHash(hash).map(r -> transactionRecordMapper.fromJson(r.getResponseBody())).orElse(null);
    }

    public IdempotencyRecordDTO reserveIdempotency(String key, User currentUser) {
        var hash = hashIdempotencyKey(key);

        IdempotencyRecord idempotency = new IdempotencyRecord();
        idempotency.setKeyHash(hash);
        idempotency.setEndpoint("/api/plus/transaction-records/send");
        idempotency.setUserId(currentUser.getId());
        idempotency.setResponseStatus(0); // pending

        IdempotencyRecordDTO mapperToDto = idempotencyRecordMapper.toDto(idempotency);

        return idempotencyRecordService.save(mapperToDto);
    }

    public void completeIdempotency(IdempotencyRecordDTO idempotency, TransactionRecordDTO transaction) throws JsonProcessingException {
        idempotency.setTransactionReference(transaction.getTransactionReference());
        idempotency.setResponseStatus(200);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String responseJson = mapper.writeValueAsString(transaction);

        idempotency.setResponseBody(responseJson);
        idempotency.setResponseStatus(200);

        idempotencyRecordService.save(idempotency);
    }

    private String hashIdempotencyKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
