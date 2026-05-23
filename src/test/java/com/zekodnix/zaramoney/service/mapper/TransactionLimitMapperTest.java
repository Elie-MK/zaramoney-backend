package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.TransactionLimitAsserts.*;
import static com.zekodnix.zaramoney.domain.TransactionLimitTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionLimitMapperTest {

    private TransactionLimitMapper transactionLimitMapper;

    @BeforeEach
    void setUp() {
        transactionLimitMapper = new TransactionLimitMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionLimitSample1();
        var actual = transactionLimitMapper.toEntity(transactionLimitMapper.toDto(expected));
        assertTransactionLimitAllPropertiesEquals(expected, actual);
    }
}
