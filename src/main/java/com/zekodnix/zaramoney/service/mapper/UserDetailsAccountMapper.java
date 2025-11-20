package com.zekodnix.zaramoney.service.mapper;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link UserDetailsAccount} and its DTO {@link UserDetailsAccountDTO}.
 */
@Mapper(componentModel = "spring")
public interface UserDetailsAccountMapper extends EntityMapper<UserDetailsAccountDTO, UserDetailsAccount> {
    @Mapping(target = "userLogin", source = "userLogin", qualifiedByName = "userLogin")
    UserDetailsAccountDTO toDto(UserDetailsAccount s);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);
}
