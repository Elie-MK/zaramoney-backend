package com.zekodnix.zaramoney.web.rest.vm;

import com.zekodnix.zaramoney.domain.enumeration.FraudReviewOutcome;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/admin/backoffice/fraud/transactions/{id}/review}. */
public class FraudReviewVM {

    @NotNull
    private FraudReviewOutcome outcome;

    public FraudReviewOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(FraudReviewOutcome outcome) {
        this.outcome = outcome;
    }
}
