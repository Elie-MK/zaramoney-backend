package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class IdempotencyRecordCriteriaTest {

    @Test
    void newIdempotencyRecordCriteriaHasAllFiltersNullTest() {
        var idempotencyRecordCriteria = new IdempotencyRecordCriteria();
        assertThat(idempotencyRecordCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void idempotencyRecordCriteriaFluentMethodsCreatesFiltersTest() {
        var idempotencyRecordCriteria = new IdempotencyRecordCriteria();

        setAllFilters(idempotencyRecordCriteria);

        assertThat(idempotencyRecordCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void idempotencyRecordCriteriaCopyCreatesNullFilterTest() {
        var idempotencyRecordCriteria = new IdempotencyRecordCriteria();
        var copy = idempotencyRecordCriteria.copy();

        assertThat(idempotencyRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(idempotencyRecordCriteria)
        );
    }

    @Test
    void idempotencyRecordCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var idempotencyRecordCriteria = new IdempotencyRecordCriteria();
        setAllFilters(idempotencyRecordCriteria);

        var copy = idempotencyRecordCriteria.copy();

        assertThat(idempotencyRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(idempotencyRecordCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var idempotencyRecordCriteria = new IdempotencyRecordCriteria();

        assertThat(idempotencyRecordCriteria).hasToString("IdempotencyRecordCriteria{}");
    }

    private static void setAllFilters(IdempotencyRecordCriteria idempotencyRecordCriteria) {
        idempotencyRecordCriteria.id();
        idempotencyRecordCriteria.keyHash();
        idempotencyRecordCriteria.endpoint();
        idempotencyRecordCriteria.userId();
        idempotencyRecordCriteria.createdAt();
        idempotencyRecordCriteria.responseStatus();
        idempotencyRecordCriteria.transactionReference();
        idempotencyRecordCriteria.distinct();
    }

    private static Condition<IdempotencyRecordCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getKeyHash()) &&
                condition.apply(criteria.getEndpoint()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getResponseStatus()) &&
                condition.apply(criteria.getTransactionReference()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<IdempotencyRecordCriteria> copyFiltersAre(
        IdempotencyRecordCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getKeyHash(), copy.getKeyHash()) &&
                condition.apply(criteria.getEndpoint(), copy.getEndpoint()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getResponseStatus(), copy.getResponseStatus()) &&
                condition.apply(criteria.getTransactionReference(), copy.getTransactionReference()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
