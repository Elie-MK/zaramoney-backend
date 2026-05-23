package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class LedgerEntryTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static LedgerEntry getLedgerEntrySample1() {
        return new LedgerEntry().id(1L);
    }

    public static LedgerEntry getLedgerEntrySample2() {
        return new LedgerEntry().id(2L);
    }

    public static LedgerEntry getLedgerEntryRandomSampleGenerator() {
        return new LedgerEntry().id(longCount.incrementAndGet());
    }
}
