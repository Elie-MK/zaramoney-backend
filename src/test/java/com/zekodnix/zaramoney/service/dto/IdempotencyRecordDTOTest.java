package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class IdempotencyRecordDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(IdempotencyRecordDTO.class);
        IdempotencyRecordDTO idempotencyRecordDTO1 = new IdempotencyRecordDTO();
        idempotencyRecordDTO1.setId(1L);
        IdempotencyRecordDTO idempotencyRecordDTO2 = new IdempotencyRecordDTO();
        assertThat(idempotencyRecordDTO1).isNotEqualTo(idempotencyRecordDTO2);
        idempotencyRecordDTO2.setId(idempotencyRecordDTO1.getId());
        assertThat(idempotencyRecordDTO1).isEqualTo(idempotencyRecordDTO2);
        idempotencyRecordDTO2.setId(2L);
        assertThat(idempotencyRecordDTO1).isNotEqualTo(idempotencyRecordDTO2);
        idempotencyRecordDTO1.setId(null);
        assertThat(idempotencyRecordDTO1).isNotEqualTo(idempotencyRecordDTO2);
    }
}
