package com.zekodnix.zaramoney.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class NotificationCriteriaTest {

    @Test
    void newNotificationCriteriaHasAllFiltersNullTest() {
        var notificationCriteria = new NotificationCriteria();
        assertThat(notificationCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void notificationCriteriaFluentMethodsCreatesFiltersTest() {
        var notificationCriteria = new NotificationCriteria();

        setAllFilters(notificationCriteria);

        assertThat(notificationCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void notificationCriteriaCopyCreatesNullFilterTest() {
        var notificationCriteria = new NotificationCriteria();
        var copy = notificationCriteria.copy();

        assertThat(notificationCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(notificationCriteria)
        );
    }

    @Test
    void notificationCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var notificationCriteria = new NotificationCriteria();
        setAllFilters(notificationCriteria);

        var copy = notificationCriteria.copy();

        assertThat(notificationCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(notificationCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var notificationCriteria = new NotificationCriteria();

        assertThat(notificationCriteria).hasToString("NotificationCriteria{}");
    }

    private static void setAllFilters(NotificationCriteria notificationCriteria) {
        notificationCriteria.id();
        notificationCriteria.title();
        notificationCriteria.message();
        notificationCriteria.type();
        notificationCriteria.channel();
        notificationCriteria.status();
        notificationCriteria.isRead();
        notificationCriteria.deepLink();
        notificationCriteria.createdAt();
        notificationCriteria.sentAt();
        notificationCriteria.userId();
        notificationCriteria.transactionId();
        notificationCriteria.distinct();
    }

    private static Condition<NotificationCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getTitle()) &&
                condition.apply(criteria.getMessage()) &&
                condition.apply(criteria.getType()) &&
                condition.apply(criteria.getChannel()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getIsRead()) &&
                condition.apply(criteria.getDeepLink()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getSentAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getTransactionId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<NotificationCriteria> copyFiltersAre(
        NotificationCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getTitle(), copy.getTitle()) &&
                condition.apply(criteria.getMessage(), copy.getMessage()) &&
                condition.apply(criteria.getType(), copy.getType()) &&
                condition.apply(criteria.getChannel(), copy.getChannel()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getIsRead(), copy.getIsRead()) &&
                condition.apply(criteria.getDeepLink(), copy.getDeepLink()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getSentAt(), copy.getSentAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getTransactionId(), copy.getTransactionId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
