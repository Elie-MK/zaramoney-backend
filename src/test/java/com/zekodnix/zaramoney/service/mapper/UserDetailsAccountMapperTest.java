package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.UserDetailsAccountAsserts.*;
import static com.zekodnix.zaramoney.domain.UserDetailsAccountTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDetailsAccountMapperTest {

    private UserDetailsAccountMapper userDetailsAccountMapper;

    @BeforeEach
    void setUp() {
        userDetailsAccountMapper = new UserDetailsAccountMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getUserDetailsAccountSample1();
        var actual = userDetailsAccountMapper.toEntity(userDetailsAccountMapper.toDto(expected));
        assertUserDetailsAccountAllPropertiesEquals(expected, actual);
    }
}
