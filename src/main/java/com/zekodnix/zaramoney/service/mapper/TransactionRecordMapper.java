package com.zekodnix.zaramoney.service.mapper;

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
}
