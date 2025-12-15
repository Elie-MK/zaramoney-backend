package com.zekodnix.zaramoney.service.mapper;

import static com.zekodnix.zaramoney.domain.IdempotencyRecordAsserts.*;
import static com.zekodnix.zaramoney.domain.IdempotencyRecordTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdempotencyRecordMapperTest {

    private IdempotencyRecordMapper idempotencyRecordMapper;

    @BeforeEach
    void setUp() {
        idempotencyRecordMapper = new IdempotencyRecordMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getIdempotencyRecordSample1();
        var actual = idempotencyRecordMapper.toEntity(idempotencyRecordMapper.toDto(expected));
        assertIdempotencyRecordAllPropertiesEquals(expected, actual);
    }
}
