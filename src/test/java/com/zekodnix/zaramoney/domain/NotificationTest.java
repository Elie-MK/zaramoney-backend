package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.NotificationTestSamples.*;
import static com.zekodnix.zaramoney.domain.TransactionRecordTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Notification.class);
        Notification notification1 = getNotificationSample1();
        Notification notification2 = new Notification();
        assertThat(notification1).isNotEqualTo(notification2);

        notification2.setId(notification1.getId());
        assertThat(notification1).isEqualTo(notification2);

        notification2 = getNotificationSample2();
        assertThat(notification1).isNotEqualTo(notification2);
    }

    @Test
    void transactionTest() {
        Notification notification = getNotificationRandomSampleGenerator();
        TransactionRecord transactionRecordBack = getTransactionRecordRandomSampleGenerator();

        notification.setTransaction(transactionRecordBack);
        assertThat(notification.getTransaction()).isEqualTo(transactionRecordBack);

        notification.transaction(null);
        assertThat(notification.getTransaction()).isNull();
    }
}
