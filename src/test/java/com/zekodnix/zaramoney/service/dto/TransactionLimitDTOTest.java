package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionLimitDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionLimitDTO.class);
        TransactionLimitDTO transactionLimitDTO1 = new TransactionLimitDTO();
        transactionLimitDTO1.setId(1L);
        TransactionLimitDTO transactionLimitDTO2 = new TransactionLimitDTO();
        assertThat(transactionLimitDTO1).isNotEqualTo(transactionLimitDTO2);
        transactionLimitDTO2.setId(transactionLimitDTO1.getId());
        assertThat(transactionLimitDTO1).isEqualTo(transactionLimitDTO2);
        transactionLimitDTO2.setId(2L);
        assertThat(transactionLimitDTO1).isNotEqualTo(transactionLimitDTO2);
        transactionLimitDTO1.setId(null);
        assertThat(transactionLimitDTO1).isNotEqualTo(transactionLimitDTO2);
    }
}
