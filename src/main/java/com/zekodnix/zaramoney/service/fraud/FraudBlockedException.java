package com.zekodnix.zaramoney.service.fraud;

import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;

/**
 * Thrown inside the money transaction when a payment is BLOCKED in ENFORCE mode: the transaction rolls back (no money
 * moves, idempotency key released), then the caller records {@code attempt} (status FAILED, fraud BLOCKED) in a new
 * transaction for the admin review queue and answers 403 {@code transactionblocked}.
 */
public class FraudBlockedException extends RuntimeException {

    private final transient TransactionRecordDTO attempt;
    private final transient FraudAssessment assessment;

    public FraudBlockedException(TransactionRecordDTO attempt, FraudAssessment assessment) {
        super("Payment blocked by fraud rules: " + assessment.reasonsText(), null, false, false);
        this.attempt = attempt;
        this.assessment = assessment;
    }

    public TransactionRecordDTO getAttempt() {
        return attempt;
    }

    public FraudAssessment getAssessment() {
        return assessment;
    }
}
