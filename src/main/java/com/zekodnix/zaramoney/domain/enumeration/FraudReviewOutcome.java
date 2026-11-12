package com.zekodnix.zaramoney.domain.enumeration;

/**
 * Admin decision on a SUSPICIOUS / BLOCKED transaction (fraud review queue).
 */
public enum FraudReviewOutcome {
    /** Legitimate: nothing to do. */
    CLEARED,
    /** Fraud confirmed: follow up outside the app (suspend the account, contact the user, report). */
    CONFIRMED,
}
