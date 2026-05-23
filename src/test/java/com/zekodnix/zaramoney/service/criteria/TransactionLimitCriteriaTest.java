package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TransactionLimitCriteriaTest {

    @Test
    void newTransactionLimitCriteriaHasAllFiltersNullTest() {
        var transactionLimitCriteria = new TransactionLimitCriteria();
        assertThat(transactionLimitCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void transactionLimitCriteriaFluentMethodsCreatesFiltersTest() {
        var transactionLimitCriteria = new TransactionLimitCriteria();

        setAllFilters(transactionLimitCriteria);

        assertThat(transactionLimitCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void transactionLimitCriteriaCopyCreatesNullFilterTest() {
        var transactionLimitCriteria = new TransactionLimitCriteria();
        var copy = transactionLimitCriteria.copy();

        assertThat(transactionLimitCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionLimitCriteria)
        );
    }

    @Test
    void transactionLimitCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var transactionLimitCriteria = new TransactionLimitCriteria();
        setAllFilters(transactionLimitCriteria);

        var copy = transactionLimitCriteria.copy();

        assertThat(transactionLimitCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionLimitCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var transactionLimitCriteria = new TransactionLimitCriteria();

        assertThat(transactionLimitCriteria).hasToString("TransactionLimitCriteria{}");
    }

    private static void setAllFilters(TransactionLimitCriteria transactionLimitCriteria) {
        transactionLimitCriteria.id();
        transactionLimitCriteria.dailyLimit();
        transactionLimitCriteria.monthlyLimit();
        transactionLimitCriteria.accountId();
        transactionLimitCriteria.distinct();
    }

    private static Condition<TransactionLimitCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getDailyLimit()) &&
                condition.apply(criteria.getMonthlyLimit()) &&
                condition.apply(criteria.getAccountId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TransactionLimitCriteria> copyFiltersAre(
        TransactionLimitCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getDailyLimit(), copy.getDailyLimit()) &&
                condition.apply(criteria.getMonthlyLimit(), copy.getMonthlyLimit()) &&
                condition.apply(criteria.getAccountId(), copy.getAccountId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
