package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class UserDetailsAccountCriteriaTest {

    @Test
    void newUserDetailsAccountCriteriaHasAllFiltersNullTest() {
        var userDetailsAccountCriteria = new UserDetailsAccountCriteria();
        assertThat(userDetailsAccountCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void userDetailsAccountCriteriaFluentMethodsCreatesFiltersTest() {
        var userDetailsAccountCriteria = new UserDetailsAccountCriteria();

        setAllFilters(userDetailsAccountCriteria);

        assertThat(userDetailsAccountCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void userDetailsAccountCriteriaCopyCreatesNullFilterTest() {
        var userDetailsAccountCriteria = new UserDetailsAccountCriteria();
        var copy = userDetailsAccountCriteria.copy();

        assertThat(userDetailsAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(userDetailsAccountCriteria)
        );
    }

    @Test
    void userDetailsAccountCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var userDetailsAccountCriteria = new UserDetailsAccountCriteria();
        setAllFilters(userDetailsAccountCriteria);

        var copy = userDetailsAccountCriteria.copy();

        assertThat(userDetailsAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(userDetailsAccountCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var userDetailsAccountCriteria = new UserDetailsAccountCriteria();

        assertThat(userDetailsAccountCriteria).hasToString("UserDetailsAccountCriteria{}");
    }

    private static void setAllFilters(UserDetailsAccountCriteria userDetailsAccountCriteria) {
        userDetailsAccountCriteria.id();
        userDetailsAccountCriteria.phoneNumber();
        userDetailsAccountCriteria.facePicture();
        userDetailsAccountCriteria.idCardPicture();
        userDetailsAccountCriteria.country();
        userDetailsAccountCriteria.address();
        userDetailsAccountCriteria.isAgent();
        userDetailsAccountCriteria.kycStatus();
        userDetailsAccountCriteria.expoPushToken();
        userDetailsAccountCriteria.userId();
        userDetailsAccountCriteria.distinct();
    }

    private static Condition<UserDetailsAccountCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getPhoneNumber()) &&
                condition.apply(criteria.getFacePicture()) &&
                condition.apply(criteria.getIdCardPicture()) &&
                condition.apply(criteria.getCountry()) &&
                condition.apply(criteria.getAddress()) &&
                condition.apply(criteria.getIsAgent()) &&
                condition.apply(criteria.getKycStatus()) &&
                condition.apply(criteria.getExpoPushToken()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<UserDetailsAccountCriteria> copyFiltersAre(
        UserDetailsAccountCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getPhoneNumber(), copy.getPhoneNumber()) &&
                condition.apply(criteria.getFacePicture(), copy.getFacePicture()) &&
                condition.apply(criteria.getIdCardPicture(), copy.getIdCardPicture()) &&
                condition.apply(criteria.getCountry(), copy.getCountry()) &&
                condition.apply(criteria.getAddress(), copy.getAddress()) &&
                condition.apply(criteria.getIsAgent(), copy.getIsAgent()) &&
                condition.apply(criteria.getKycStatus(), copy.getKycStatus()) &&
                condition.apply(criteria.getExpoPushToken(), copy.getExpoPushToken()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
