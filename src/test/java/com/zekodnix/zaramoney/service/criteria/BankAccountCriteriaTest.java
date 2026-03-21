package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class BankAccountCriteriaTest {

    @Test
    void newBankAccountCriteriaHasAllFiltersNullTest() {
        var bankAccountCriteria = new BankAccountCriteria();
        assertThat(bankAccountCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void bankAccountCriteriaFluentMethodsCreatesFiltersTest() {
        var bankAccountCriteria = new BankAccountCriteria();

        setAllFilters(bankAccountCriteria);

        assertThat(bankAccountCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void bankAccountCriteriaCopyCreatesNullFilterTest() {
        var bankAccountCriteria = new BankAccountCriteria();
        var copy = bankAccountCriteria.copy();

        assertThat(bankAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(bankAccountCriteria)
        );
    }

    @Test
    void bankAccountCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var bankAccountCriteria = new BankAccountCriteria();
        setAllFilters(bankAccountCriteria);

        var copy = bankAccountCriteria.copy();

        assertThat(bankAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(bankAccountCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var bankAccountCriteria = new BankAccountCriteria();

        assertThat(bankAccountCriteria).hasToString("BankAccountCriteria{}");
    }

    private static void setAllFilters(BankAccountCriteria bankAccountCriteria) {
        bankAccountCriteria.id();
        bankAccountCriteria.accountNumber();
        bankAccountCriteria.balance();
        bankAccountCriteria.currency();
        bankAccountCriteria.userId();
        bankAccountCriteria.distinct();
    }

    private static Condition<BankAccountCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getAccountNumber()) &&
                condition.apply(criteria.getBalance()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<BankAccountCriteria> copyFiltersAre(BankAccountCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getAccountNumber(), copy.getAccountNumber()) &&
                condition.apply(criteria.getBalance(), copy.getBalance()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
