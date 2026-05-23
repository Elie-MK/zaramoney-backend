package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionFeeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionFeeDTO.class);
        TransactionFeeDTO transactionFeeDTO1 = new TransactionFeeDTO();
        transactionFeeDTO1.setId(1L);
        TransactionFeeDTO transactionFeeDTO2 = new TransactionFeeDTO();
        assertThat(transactionFeeDTO1).isNotEqualTo(transactionFeeDTO2);
        transactionFeeDTO2.setId(transactionFeeDTO1.getId());
        assertThat(transactionFeeDTO1).isEqualTo(transactionFeeDTO2);
        transactionFeeDTO2.setId(2L);
        assertThat(transactionFeeDTO1).isNotEqualTo(transactionFeeDTO2);
        transactionFeeDTO1.setId(null);
        assertThat(transactionFeeDTO1).isNotEqualTo(transactionFeeDTO2);
    }
}
