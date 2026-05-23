package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionLimit;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionLimit} and its DTO {@link TransactionLimitDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionLimitMapper extends EntityMapper<TransactionLimitDTO, TransactionLimit> {
    @Mapping(target = "account", source = "account", qualifiedByName = "bankAccountAccountNumber")
    TransactionLimitDTO toDto(TransactionLimit s);

    @Named("bankAccountAccountNumber")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "accountNumber", source = "accountNumber")
    BankAccountDTO toDtoBankAccountAccountNumber(BankAccount bankAccount);
}
