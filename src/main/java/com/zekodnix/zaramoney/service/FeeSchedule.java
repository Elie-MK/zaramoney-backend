package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Fees.Tier;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Fees and the minimum-balance rule, from {@code application.fees} (see {@link ApplicationProperties.Fees}).
 * The configuration is validated at startup: a broken schedule stops the app instead of charging wrong fees.
 */
@Service
public class FeeSchedule {

    private final ApplicationProperties.Fees fees;

    public FeeSchedule(ApplicationProperties applicationProperties) {
        this.fees = applicationProperties.getFees();
    }

    @PostConstruct
    void validate() {
        validateTiers("transfer", fees.getTransfer());
        validateTiers("withdrawal", fees.getWithdrawal());
        if (fees.getMinBalance() == null || fees.getMinBalance().signum() < 0) {
            throw new IllegalStateException("application.fees.min-balance must be >= 0");
        }
    }

    public BigDecimal transferFee(BigDecimal amount) {
        return apply(fees.getTransfer(), amount);
    }

    public BigDecimal withdrawalFee(BigDecimal amount) {
        return apply(fees.getWithdrawal(), amount);
    }

    public BigDecimal minBalance() {
        return fees.getMinBalance();
    }

    static BigDecimal apply(List<Tier> tiers, BigDecimal amount) {
        for (Tier tier : tiers) {
            if (tier.getUpTo() == null || amount.compareTo(tier.getUpTo()) <= 0) {
                return amount.multiply(tier.getRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        throw new IllegalStateException("No fee tier for " + amount); // impossible after validate()
    }

    static void validateTiers(String name, List<Tier> tiers) {
        String prefix = "application.fees." + name + ": ";
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalStateException(prefix + "at least one tier is required");
        }
        BigDecimal previous = null;
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            boolean last = i == tiers.size() - 1;
            if (tier.getRate() == null || tier.getRate().signum() < 0 || tier.getRate().compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalStateException(prefix + "tier " + (i + 1) + " needs a rate in [0, 1)");
            }
            if (last != (tier.getUpTo() == null)) {
                throw new IllegalStateException(prefix + "only the last tier has no up-to");
            }
            if (tier.getUpTo() != null) {
                if (previous != null && tier.getUpTo().compareTo(previous) <= 0) {
                    throw new IllegalStateException(prefix + "up-to values must increase");
                }
                previous = tier.getUpTo();
            }
        }
    }
}
