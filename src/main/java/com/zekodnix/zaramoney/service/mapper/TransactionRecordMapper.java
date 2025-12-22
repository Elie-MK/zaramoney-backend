package com.zekodnix.zaramoney.service.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
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
