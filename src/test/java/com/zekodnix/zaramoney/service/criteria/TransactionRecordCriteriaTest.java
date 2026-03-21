package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TransactionRecordCriteriaTest {

    @Test
    void newTransactionRecordCriteriaHasAllFiltersNullTest() {
        var transactionRecordCriteria = new TransactionRecordCriteria();
        assertThat(transactionRecordCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void transactionRecordCriteriaFluentMethodsCreatesFiltersTest() {
        var transactionRecordCriteria = new TransactionRecordCriteria();

        setAllFilters(transactionRecordCriteria);

        assertThat(transactionRecordCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void transactionRecordCriteriaCopyCreatesNullFilterTest() {
        var transactionRecordCriteria = new TransactionRecordCriteria();
        var copy = transactionRecordCriteria.copy();

        assertThat(transactionRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionRecordCriteria)
        );
    }

    @Test
    void transactionRecordCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var transactionRecordCriteria = new TransactionRecordCriteria();
        setAllFilters(transactionRecordCriteria);

        var copy = transactionRecordCriteria.copy();

        assertThat(transactionRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionRecordCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var transactionRecordCriteria = new TransactionRecordCriteria();

        assertThat(transactionRecordCriteria).hasToString("TransactionRecordCriteria{}");
    }

    private static void setAllFilters(TransactionRecordCriteria transactionRecordCriteria) {
        transactionRecordCriteria.id();
        transactionRecordCriteria.transactionType();
        transactionRecordCriteria.sendAmount();
        transactionRecordCriteria.receiveAmount();
        transactionRecordCriteria.transactionDate();
        transactionRecordCriteria.description();
        transactionRecordCriteria.currencySendAmount();
        transactionRecordCriteria.currencyReceiveAmount();
        transactionRecordCriteria.transactionStatus();
        transactionRecordCriteria.transactionReference();
        transactionRecordCriteria.riskScore();
        transactionRecordCriteria.fraudStatus();
        transactionRecordCriteria.createdAt();
        transactionRecordCriteria.updatedAt();
        transactionRecordCriteria.senderId();
        transactionRecordCriteria.receiverId();
        transactionRecordCriteria.distinct();
    }

    private static Condition<TransactionRecordCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getTransactionType()) &&
                condition.apply(criteria.getSendAmount()) &&
                condition.apply(criteria.getReceiveAmount()) &&
                condition.apply(criteria.getTransactionDate()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getCurrencySendAmount()) &&
                condition.apply(criteria.getCurrencyReceiveAmount()) &&
                condition.apply(criteria.getTransactionStatus()) &&
                condition.apply(criteria.getTransactionReference()) &&
                condition.apply(criteria.getRiskScore()) &&
                condition.apply(criteria.getFraudStatus()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getSenderId()) &&
                condition.apply(criteria.getReceiverId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TransactionRecordCriteria> copyFiltersAre(
        TransactionRecordCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getTransactionType(), copy.getTransactionType()) &&
                condition.apply(criteria.getSendAmount(), copy.getSendAmount()) &&
                condition.apply(criteria.getReceiveAmount(), copy.getReceiveAmount()) &&
                condition.apply(criteria.getTransactionDate(), copy.getTransactionDate()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getCurrencySendAmount(), copy.getCurrencySendAmount()) &&
                condition.apply(criteria.getCurrencyReceiveAmount(), copy.getCurrencyReceiveAmount()) &&
                condition.apply(criteria.getTransactionStatus(), copy.getTransactionStatus()) &&
                condition.apply(criteria.getTransactionReference(), copy.getTransactionReference()) &&
                condition.apply(criteria.getRiskScore(), copy.getRiskScore()) &&
                condition.apply(criteria.getFraudStatus(), copy.getFraudStatus()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getSenderId(), copy.getSenderId()) &&
                condition.apply(criteria.getReceiverId(), copy.getReceiverId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
