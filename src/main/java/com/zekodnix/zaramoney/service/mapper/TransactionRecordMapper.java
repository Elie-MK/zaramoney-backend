package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionRecord} and its DTO {@link TransactionRecordDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionRecordMapper extends EntityMapper<TransactionRecordDTO, TransactionRecord> {
    @Mapping(target = "userLogin", source = "userLogin", qualifiedByName = "userLogin")
    TransactionRecordDTO toDto(TransactionRecord s);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    // -----------------------------
    // JSON helpers for idempotency
    // -----------------------------
    default String toJson(TransactionRecordDTO dto) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TransactionRecordDTO", e);
        }
    }

    default TransactionRecordDTO fromJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, TransactionRecordDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize TransactionRecordDTO", e);
        }
    }
}
