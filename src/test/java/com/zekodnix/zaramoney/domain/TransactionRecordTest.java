package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.BankAccountTestSamples.*;
import static com.zekodnix.zaramoney.domain.TransactionRecordTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionRecordTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRecord.class);
        TransactionRecord transactionRecord1 = getTransactionRecordSample1();
        TransactionRecord transactionRecord2 = new TransactionRecord();
        assertThat(transactionRecord1).isNotEqualTo(transactionRecord2);

        transactionRecord2.setId(transactionRecord1.getId());
        assertThat(transactionRecord1).isEqualTo(transactionRecord2);

        transactionRecord2 = getTransactionRecordSample2();
        assertThat(transactionRecord1).isNotEqualTo(transactionRecord2);
    }

    @Test
    void senderTest() {
        TransactionRecord transactionRecord = getTransactionRecordRandomSampleGenerator();
        BankAccount bankAccountBack = getBankAccountRandomSampleGenerator();

        transactionRecord.setSender(bankAccountBack);
        assertThat(transactionRecord.getSender()).isEqualTo(bankAccountBack);

        transactionRecord.sender(null);
        assertThat(transactionRecord.getSender()).isNull();
    }

    @Test
    void receiverTest() {
        TransactionRecord transactionRecord = getTransactionRecordRandomSampleGenerator();
        BankAccount bankAccountBack = getBankAccountRandomSampleGenerator();

        transactionRecord.setReceiver(bankAccountBack);
        assertThat(transactionRecord.getReceiver()).isEqualTo(bankAccountBack);

        transactionRecord.receiver(null);
        assertThat(transactionRecord.getReceiver()).isNull();
    }
}
