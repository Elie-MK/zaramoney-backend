package com.zekodnix.zaramoney.domain.enumeration;

/** What an {@code AuditEvent} records. Stored as text: add values freely, never rename existing ones. */
public enum AuditAction {
    // admin: money / reviews
    DEPOSIT,
    KYC_APPROVED,
    KYC_REJECTED,
    FRAUD_REVIEWED,
    // admin: accounts and users
    BANK_ACCOUNT_CREATED,
    BANK_ACCOUNT_UPDATED,
    BANK_ACCOUNT_DELETED,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_BLOCKED,
    USER_UNBLOCKED,
    DEVICE_BLOCKED,
    DEVICE_UNBLOCKED,
    SESSIONS_REVOKED,
    // customer: sensitive
    PAYMENT_BLOCKED,
    PIN_SET,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
}
