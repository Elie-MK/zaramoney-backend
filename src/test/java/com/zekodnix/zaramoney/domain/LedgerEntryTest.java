package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.BankAccountTestSamples.*;
import static com.zekodnix.zaramoney.domain.LedgerEntryTestSamples.*;
import static com.zekodnix.zaramoney.domain.TransactionRecordTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(LedgerEntry.class);
        LedgerEntry ledgerEntry1 = getLedgerEntrySample1();
        LedgerEntry ledgerEntry2 = new LedgerEntry();
        assertThat(ledgerEntry1).isNotEqualTo(ledgerEntry2);

        ledgerEntry2.setId(ledgerEntry1.getId());
        assertThat(ledgerEntry1).isEqualTo(ledgerEntry2);

        ledgerEntry2 = getLedgerEntrySample2();
        assertThat(ledgerEntry1).isNotEqualTo(ledgerEntry2);
    }

    @Test
    void accountTest() {
        LedgerEntry ledgerEntry = getLedgerEntryRandomSampleGenerator();
        BankAccount bankAccountBack = getBankAccountRandomSampleGenerator();

        ledgerEntry.setAccount(bankAccountBack);
        assertThat(ledgerEntry.getAccount()).isEqualTo(bankAccountBack);

        ledgerEntry.account(null);
        assertThat(ledgerEntry.getAccount()).isNull();
    }

    @Test
    void transactionTest() {
        LedgerEntry ledgerEntry = getLedgerEntryRandomSampleGenerator();
        TransactionRecord transactionRecordBack = getTransactionRecordRandomSampleGenerator();

        ledgerEntry.setTransaction(transactionRecordBack);
        assertThat(ledgerEntry.getTransaction()).isEqualTo(transactionRecordBack);

        ledgerEntry.transaction(null);
        assertThat(ledgerEntry.getTransaction()).isNull();
    }
}
