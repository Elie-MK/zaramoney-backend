package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TransactionFeeCriteriaTest {

    @Test
    void newTransactionFeeCriteriaHasAllFiltersNullTest() {
        var transactionFeeCriteria = new TransactionFeeCriteria();
        assertThat(transactionFeeCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void transactionFeeCriteriaFluentMethodsCreatesFiltersTest() {
        var transactionFeeCriteria = new TransactionFeeCriteria();

        setAllFilters(transactionFeeCriteria);

        assertThat(transactionFeeCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void transactionFeeCriteriaCopyCreatesNullFilterTest() {
        var transactionFeeCriteria = new TransactionFeeCriteria();
        var copy = transactionFeeCriteria.copy();

        assertThat(transactionFeeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionFeeCriteria)
        );
    }

    @Test
    void transactionFeeCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var transactionFeeCriteria = new TransactionFeeCriteria();
        setAllFilters(transactionFeeCriteria);

        var copy = transactionFeeCriteria.copy();

        assertThat(transactionFeeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionFeeCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var transactionFeeCriteria = new TransactionFeeCriteria();

        assertThat(transactionFeeCriteria).hasToString("TransactionFeeCriteria{}");
    }

    private static void setAllFilters(TransactionFeeCriteria transactionFeeCriteria) {
        transactionFeeCriteria.id();
        transactionFeeCriteria.amount();
        transactionFeeCriteria.currency();
        transactionFeeCriteria.type();
        transactionFeeCriteria.transactionId();
        transactionFeeCriteria.distinct();
    }

    private static Condition<TransactionFeeCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getAmount()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getType()) &&
                condition.apply(criteria.getTransactionId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TransactionFeeCriteria> copyFiltersAre(
        TransactionFeeCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getAmount(), copy.getAmount()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getType(), copy.getType()) &&
                condition.apply(criteria.getTransactionId(), copy.getTransactionId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
