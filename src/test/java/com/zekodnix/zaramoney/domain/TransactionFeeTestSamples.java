package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionFeeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static TransactionFee getTransactionFeeSample1() {
        return new TransactionFee().id(1L).type("type1");
    }

    public static TransactionFee getTransactionFeeSample2() {
        return new TransactionFee().id(2L).type("type2");
    }

    public static TransactionFee getTransactionFeeRandomSampleGenerator() {
        return new TransactionFee().id(longCount.incrementAndGet()).type(UUID.randomUUID().toString());
    }
}
