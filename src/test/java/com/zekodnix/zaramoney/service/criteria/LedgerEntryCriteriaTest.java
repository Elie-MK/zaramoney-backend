package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class LedgerEntryCriteriaTest {

    @Test
    void newLedgerEntryCriteriaHasAllFiltersNullTest() {
        var ledgerEntryCriteria = new LedgerEntryCriteria();
        assertThat(ledgerEntryCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void ledgerEntryCriteriaFluentMethodsCreatesFiltersTest() {
        var ledgerEntryCriteria = new LedgerEntryCriteria();

        setAllFilters(ledgerEntryCriteria);

        assertThat(ledgerEntryCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void ledgerEntryCriteriaCopyCreatesNullFilterTest() {
        var ledgerEntryCriteria = new LedgerEntryCriteria();
        var copy = ledgerEntryCriteria.copy();

        assertThat(ledgerEntryCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(ledgerEntryCriteria)
        );
    }

    @Test
    void ledgerEntryCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var ledgerEntryCriteria = new LedgerEntryCriteria();
        setAllFilters(ledgerEntryCriteria);

        var copy = ledgerEntryCriteria.copy();

        assertThat(ledgerEntryCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(ledgerEntryCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var ledgerEntryCriteria = new LedgerEntryCriteria();

        assertThat(ledgerEntryCriteria).hasToString("LedgerEntryCriteria{}");
    }

    private static void setAllFilters(LedgerEntryCriteria ledgerEntryCriteria) {
        ledgerEntryCriteria.id();
        ledgerEntryCriteria.amount();
        ledgerEntryCriteria.currency();
        ledgerEntryCriteria.entryType();
        ledgerEntryCriteria.createdAt();
        ledgerEntryCriteria.accountId();
        ledgerEntryCriteria.transactionId();
        ledgerEntryCriteria.distinct();
    }

    private static Condition<LedgerEntryCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getAmount()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getEntryType()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getAccountId()) &&
                condition.apply(criteria.getTransactionId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<LedgerEntryCriteria> copyFiltersAre(LedgerEntryCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getAmount(), copy.getAmount()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getEntryType(), copy.getEntryType()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getAccountId(), copy.getAccountId()) &&
                condition.apply(criteria.getTransactionId(), copy.getTransactionId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
