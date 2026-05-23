package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionLimitTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static TransactionLimit getTransactionLimitSample1() {
        return new TransactionLimit().id(1L);
    }

    public static TransactionLimit getTransactionLimitSample2() {
        return new TransactionLimit().id(2L);
    }

    public static TransactionLimit getTransactionLimitRandomSampleGenerator() {
        return new TransactionLimit().id(longCount.incrementAndGet());
    }
}
