package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionRecordTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static TransactionRecord getTransactionRecordSample1() {
        return new TransactionRecord().id(1L).description("description1").transactionReference("transactionReference1").riskScore(1);
    }

    public static TransactionRecord getTransactionRecordSample2() {
        return new TransactionRecord().id(2L).description("description2").transactionReference("transactionReference2").riskScore(2);
    }

    public static TransactionRecord getTransactionRecordRandomSampleGenerator() {
        return new TransactionRecord()
            .id(longCount.incrementAndGet())
            .description(UUID.randomUUID().toString())
            .transactionReference(UUID.randomUUID().toString())
            .riskScore(intCount.incrementAndGet());
    }
}
