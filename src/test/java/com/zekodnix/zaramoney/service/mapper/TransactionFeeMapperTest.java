package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.TransactionFeeAsserts.*;
import static com.zekodnix.zaramoney.domain.TransactionFeeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionFeeMapperTest {

    private TransactionFeeMapper transactionFeeMapper;

    @BeforeEach
    void setUp() {
        transactionFeeMapper = new TransactionFeeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionFeeSample1();
        var actual = transactionFeeMapper.toEntity(transactionFeeMapper.toDto(expected));
        assertTransactionFeeAllPropertiesEquals(expected, actual);
    }
}
