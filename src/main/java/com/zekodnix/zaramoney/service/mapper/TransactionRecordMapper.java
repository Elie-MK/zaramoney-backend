package com.zekodnix.zaramoney.service.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionRecord} and its DTO {@link TransactionRecordDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionRecordMapper extends EntityMapper<TransactionRecordDTO, TransactionRecord> {
    @Mapping(target = "sender", source = "sender", qualifiedByName = "bankAccountAccountNumber")
    @Mapping(target = "receiver", source = "receiver", qualifiedByName = "bankAccountAccountNumber")
    TransactionRecordDTO toDto(TransactionRecord s);

    @Named("bankAccountAccountNumber")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "accountNumber", source = "accountNumber")
    BankAccountDTO toDtoBankAccountAccountNumber(BankAccount bankAccount);

    default String toJson(TransactionRecordDTO dto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(dto);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TransactionRecordDTO", e);
        }
    }

    default TransactionRecordDTO fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.readValue(json, TransactionRecordDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize TransactionRecordDTO", e);
        }
    }
}
