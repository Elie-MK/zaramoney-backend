package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.TransactionFee;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionFee} and its DTO {@link TransactionFeeDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionFeeMapper extends EntityMapper<TransactionFeeDTO, TransactionFee> {
    @Mapping(target = "transaction", source = "transaction", qualifiedByName = "transactionRecordTransactionReference")
    TransactionFeeDTO toDto(TransactionFee s);

    @Named("transactionRecordTransactionReference")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "transactionReference", source = "transactionReference")
    TransactionRecordDTO toDtoTransactionRecordTransactionReference(TransactionRecord transactionRecord);
}
