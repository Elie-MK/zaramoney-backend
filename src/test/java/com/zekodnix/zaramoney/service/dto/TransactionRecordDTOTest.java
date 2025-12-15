package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionRecordDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRecordDTO.class);
        TransactionRecordDTO transactionRecordDTO1 = new TransactionRecordDTO();
        transactionRecordDTO1.setId(1L);
        TransactionRecordDTO transactionRecordDTO2 = new TransactionRecordDTO();
        assertThat(transactionRecordDTO1).isNotEqualTo(transactionRecordDTO2);
        transactionRecordDTO2.setId(transactionRecordDTO1.getId());
        assertThat(transactionRecordDTO1).isEqualTo(transactionRecordDTO2);
        transactionRecordDTO2.setId(2L);
        assertThat(transactionRecordDTO1).isNotEqualTo(transactionRecordDTO2);
        transactionRecordDTO1.setId(null);
        assertThat(transactionRecordDTO1).isNotEqualTo(transactionRecordDTO2);
    }
}
