package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.BankAccountAsserts.*;
import static com.zekodnix.zaramoney.domain.BankAccountTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankAccountMapperTest {

    private BankAccountMapper bankAccountMapper;

    @BeforeEach
    void setUp() {
        bankAccountMapper = new BankAccountMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBankAccountSample1();
        var actual = bankAccountMapper.toEntity(bankAccountMapper.toDto(expected));
        assertBankAccountAllPropertiesEquals(expected, actual);
    }
}
