package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.Notification;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Notification} and its DTO {@link NotificationDTO}.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper extends EntityMapper<NotificationDTO, Notification> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "transaction", source = "transaction", qualifiedByName = "transactionRecordTransactionReference")
    NotificationDTO toDto(Notification s);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("transactionRecordTransactionReference")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "transactionReference", source = "transactionReference")
    TransactionRecordDTO toDtoTransactionRecordTransactionReference(TransactionRecord transactionRecord);
}
