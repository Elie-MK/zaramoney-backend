package com.zekodnix.zaramoney.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class UserDetailsAccountTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static UserDetailsAccount getUserDetailsAccountSample1() {
        return new UserDetailsAccount()
            .id(1L)
            .phoneNumber("phoneNumber1")
            .facePicture("facePicture1")
            .idCardPicture("idCardPicture1")
            .country("country1")
            .address("address1")
            .accountNumber("accountNumber1");
    }

    public static UserDetailsAccount getUserDetailsAccountSample2() {
        return new UserDetailsAccount()
            .id(2L)
            .phoneNumber("phoneNumber2")
            .facePicture("facePicture2")
            .idCardPicture("idCardPicture2")
            .country("country2")
            .address("address2")
            .accountNumber("accountNumber2");
    }

    public static UserDetailsAccount getUserDetailsAccountRandomSampleGenerator() {
        return new UserDetailsAccount()
            .id(longCount.incrementAndGet())
            .phoneNumber(UUID.randomUUID().toString())
            .facePicture(UUID.randomUUID().toString())
            .idCardPicture(UUID.randomUUID().toString())
            .country(UUID.randomUUID().toString())
            .address(UUID.randomUUID().toString())
            .accountNumber(UUID.randomUUID().toString());
    }
}
