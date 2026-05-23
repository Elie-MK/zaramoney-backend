package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.LedgerEntry;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link LedgerEntry} and its DTO {@link LedgerEntryDTO}.
 */
@Mapper(componentModel = "spring")
public interface LedgerEntryMapper extends EntityMapper<LedgerEntryDTO, LedgerEntry> {
    @Mapping(target = "account", source = "account", qualifiedByName = "bankAccountAccountNumber")
    @Mapping(target = "transaction", source = "transaction", qualifiedByName = "transactionRecordTransactionReference")
    LedgerEntryDTO toDto(LedgerEntry s);

    @Named("bankAccountAccountNumber")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "accountNumber", source = "accountNumber")
    BankAccountDTO toDtoBankAccountAccountNumber(BankAccount bankAccount);

    @Named("transactionRecordTransactionReference")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "transactionReference", source = "transactionReference")
    TransactionRecordDTO toDtoTransactionRecordTransactionReference(TransactionRecord transactionRecord);
}
