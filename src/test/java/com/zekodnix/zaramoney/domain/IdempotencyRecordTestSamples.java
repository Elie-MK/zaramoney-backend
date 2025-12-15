package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IdempotencyRecordTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static IdempotencyRecord getIdempotencyRecordSample1() {
        return new IdempotencyRecord()
            .id(1L)
            .keyHash("keyHash1")
            .endpoint("endpoint1")
            .userId(1L)
            .responseStatus(1)
            .transactionReference("transactionReference1");
    }

    public static IdempotencyRecord getIdempotencyRecordSample2() {
        return new IdempotencyRecord()
            .id(2L)
            .keyHash("keyHash2")
            .endpoint("endpoint2")
            .userId(2L)
            .responseStatus(2)
            .transactionReference("transactionReference2");
    }

    public static IdempotencyRecord getIdempotencyRecordRandomSampleGenerator() {
        return new IdempotencyRecord()
            .id(longCount.incrementAndGet())
            .keyHash(UUID.randomUUID().toString())
            .endpoint(UUID.randomUUID().toString())
            .userId(longCount.incrementAndGet())
            .responseStatus(intCount.incrementAndGet())
            .transactionReference(UUID.randomUUID().toString());
    }
}
