package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.BankAccountTestSamples.*;
import static com.zekodnix.zaramoney.domain.TransactionLimitTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionLimitTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionLimit.class);
        TransactionLimit transactionLimit1 = getTransactionLimitSample1();
        TransactionLimit transactionLimit2 = new TransactionLimit();
        assertThat(transactionLimit1).isNotEqualTo(transactionLimit2);

        transactionLimit2.setId(transactionLimit1.getId());
        assertThat(transactionLimit1).isEqualTo(transactionLimit2);

        transactionLimit2 = getTransactionLimitSample2();
        assertThat(transactionLimit1).isNotEqualTo(transactionLimit2);
    }

    @Test
    void accountTest() {
        TransactionLimit transactionLimit = getTransactionLimitRandomSampleGenerator();
        BankAccount bankAccountBack = getBankAccountRandomSampleGenerator();

        transactionLimit.setAccount(bankAccountBack);
        assertThat(transactionLimit.getAccount()).isEqualTo(bankAccountBack);

        transactionLimit.account(null);
        assertThat(transactionLimit.getAccount()).isNull();
    }
}
