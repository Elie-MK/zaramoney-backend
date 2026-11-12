package com.zekodnix.zaramoney.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.config.ApplicationProperties.Fees.Tier;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeScheduleTest {

    private final FeeSchedule schedule = schedule(new ApplicationProperties());

    private static FeeSchedule schedule(ApplicationProperties props) {
        FeeSchedule s = new FeeSchedule(props);
        s.validate();
        return s;
    }

    @ParameterizedTest(name = "transfer {0} -> fee {1}")
    @CsvSource({ "1.00, 0.00", "100.99, 0.00", "101.00, 0.96", "500.00, 4.75", "2500.00, 23.75", "2500.01, 18.75", "10000.00, 75.00" })
    void transferTiers(String amount, String fee) {
        assertThat(schedule.transferFee(new BigDecimal(amount))).isEqualByComparingTo(fee);
    }

    @ParameterizedTest(name = "withdrawal {0} -> fee {1}")
    @CsvSource({ "1.00, 0.01", "5.00, 0.07", "5.01, 0.04", "20.00, 0.16", "20.01, 0.50", "50.00, 1.25", "51.00, 1.01", "1000.00, 19.90" })
    void withdrawalTiers(String amount, String fee) {
        assertThat(schedule.withdrawalFee(new BigDecimal(amount))).isEqualByComparingTo(fee);
    }

    @Test
    void withdrawalTierFiveToTwentyIsPointEightPercentNotEight() {
        // was 8% by mistake: 20 cost 1.60
        assertThat(schedule.withdrawalFee(new BigDecimal("20.00"))).isEqualByComparingTo("0.16");
    }

    @Test
    void feesAreRoundedHalfUpToCents() {
        // 5 x 1.3% = 0.065 -> 0.07
        assertThat(schedule.withdrawalFee(new BigDecimal("5.00")).scale()).isEqualTo(2);
        assertThat(schedule.withdrawalFee(new BigDecimal("5.00"))).isEqualByComparingTo("0.07");
    }

    @Test
    void defaultMinimumBalanceIsFive() {
        assertThat(schedule.minBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void brokenSchedulesAreRejectedAtStartup() {
        assertThatThrownBy(() -> FeeSchedule.validateTiers("x", List.of())).isInstanceOf(IllegalStateException.class);
        // last tier must be open-ended
        assertThatThrownBy(() -> FeeSchedule.validateTiers("x", List.of(new Tier("10", "0.01")))).isInstanceOf(IllegalStateException.class);
        // only the last tier may be open-ended
        assertThatThrownBy(() -> FeeSchedule.validateTiers("x", List.of(new Tier(null, "0.01"), new Tier(null, "0.02")))).isInstanceOf(
            IllegalStateException.class
        );
        // bounds must increase
        assertThatThrownBy(() ->
            FeeSchedule.validateTiers("x", List.of(new Tier("20", "0.01"), new Tier("10", "0.01"), new Tier(null, "0.01")))
        ).isInstanceOf(IllegalStateException.class);
        // rate in [0, 1)
        assertThatThrownBy(() -> FeeSchedule.validateTiers("x", List.of(new Tier(null, "1")))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FeeSchedule.validateTiers("x", List.of(new Tier(null, "-0.01")))).isInstanceOf(IllegalStateException.class);

        ApplicationProperties props = new ApplicationProperties();
        props.getFees().setMinBalance(new BigDecimal("-1"));
        assertThatThrownBy(() -> schedule(props)).isInstanceOf(IllegalStateException.class);
    }
}
