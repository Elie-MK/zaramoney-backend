package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.TransactionRecordAsserts.*;
import static com.zekodnix.zaramoney.domain.TransactionRecordTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRecordMapperTest {

    private TransactionRecordMapper transactionRecordMapper;

    @BeforeEach
    void setUp() {
        transactionRecordMapper = new TransactionRecordMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionRecordSample1();
        var actual = transactionRecordMapper.toEntity(transactionRecordMapper.toDto(expected));
        assertTransactionRecordAllPropertiesEquals(expected, actual);
    }
}
