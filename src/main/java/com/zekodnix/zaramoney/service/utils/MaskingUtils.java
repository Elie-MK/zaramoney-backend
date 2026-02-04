package com.zekodnix.zaramoney.service.utils;

public class MaskingUtils {

    private MaskingUtils() {}

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }

        int visibleDigits = 4;
        int maskedLength = accountNumber.length() - visibleDigits;

        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }
}
