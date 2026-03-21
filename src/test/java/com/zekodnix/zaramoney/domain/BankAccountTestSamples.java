package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BankAccountTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static BankAccount getBankAccountSample1() {
        return new BankAccount().id(1L).accountNumber("accountNumber1");
    }

    public static BankAccount getBankAccountSample2() {
        return new BankAccount().id(2L).accountNumber("accountNumber2");
    }

    public static BankAccount getBankAccountRandomSampleGenerator() {
        return new BankAccount().id(longCount.incrementAndGet()).accountNumber(UUID.randomUUID().toString());
    }
}
