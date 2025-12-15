package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.IdempotencyRecordTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(IdempotencyRecord.class);
        IdempotencyRecord idempotencyRecord1 = getIdempotencyRecordSample1();
        IdempotencyRecord idempotencyRecord2 = new IdempotencyRecord();
        assertThat(idempotencyRecord1).isNotEqualTo(idempotencyRecord2);

        idempotencyRecord2.setId(idempotencyRecord1.getId());
        assertThat(idempotencyRecord1).isEqualTo(idempotencyRecord2);

        idempotencyRecord2 = getIdempotencyRecordSample2();
        assertThat(idempotencyRecord1).isNotEqualTo(idempotencyRecord2);
    }
}
