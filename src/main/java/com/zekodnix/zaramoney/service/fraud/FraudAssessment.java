package com.zekodnix.zaramoney.service.fraud;

import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import java.util.List;

/**
 * Result of {@link FraudScoringService#assess}: score 0–100, the status it maps to, the rules that fired
 * (stored in {@code transaction_record.fraud_reasons}), and whether the payment must be rejected now
 * ({@code reject} is only true in ENFORCE mode).
 */
public record FraudAssessment(int score, FraudStatus status, List<String> reasons, boolean reject) {
    public static final FraudAssessment CLEAN = new FraudAssessment(0, FraudStatus.CLEAN, List.of(), false);

    /** "BURST:6/PT10M,NEW_ACCOUNT" (max 500 chars), or null when no rule fired. */
    public String reasonsText() {
        if (reasons.isEmpty()) {
            return null;
        }
        String text = String.join(",", reasons);
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
