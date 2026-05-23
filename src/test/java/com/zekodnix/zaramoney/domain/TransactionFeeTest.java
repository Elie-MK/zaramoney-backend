package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.TransactionFeeTestSamples.*;
import static com.zekodnix.zaramoney.domain.TransactionRecordTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionFeeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionFee.class);
        TransactionFee transactionFee1 = getTransactionFeeSample1();
        TransactionFee transactionFee2 = new TransactionFee();
        assertThat(transactionFee1).isNotEqualTo(transactionFee2);

        transactionFee2.setId(transactionFee1.getId());
        assertThat(transactionFee1).isEqualTo(transactionFee2);

        transactionFee2 = getTransactionFeeSample2();
        assertThat(transactionFee1).isNotEqualTo(transactionFee2);
    }

    @Test
    void transactionTest() {
        TransactionFee transactionFee = getTransactionFeeRandomSampleGenerator();
        TransactionRecord transactionRecordBack = getTransactionRecordRandomSampleGenerator();

        transactionFee.setTransaction(transactionRecordBack);
        assertThat(transactionFee.getTransaction()).isEqualTo(transactionRecordBack);

        transactionFee.transaction(null);
        assertThat(transactionFee.getTransaction()).isNull();
    }
}
