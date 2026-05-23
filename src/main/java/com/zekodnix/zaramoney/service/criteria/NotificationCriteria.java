package com.zekodnix.zaramoney.service.criteria;

import com.zekodnix.zaramoney.domain.enumeration.NotificationChannel;
import com.zekodnix.zaramoney.domain.enumeration.NotificationStatus;
import com.zekodnix.zaramoney.domain.enumeration.NotificationType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.Notification} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.NotificationResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /notifications?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NotificationCriteria implements Serializable, Criteria {

    /**
     * Class for filtering NotificationType
     */
    public static class NotificationTypeFilter extends Filter<NotificationType> {

        public NotificationTypeFilter() {}

        public NotificationTypeFilter(NotificationTypeFilter filter) {
            super(filter);
        }

        @Override
        public NotificationTypeFilter copy() {
            return new NotificationTypeFilter(this);
        }
    }

    /**
     * Class for filtering NotificationChannel
     */
    public static class NotificationChannelFilter extends Filter<NotificationChannel> {

        public NotificationChannelFilter() {}

        public NotificationChannelFilter(NotificationChannelFilter filter) {
            super(filter);
        }

        @Override
        public NotificationChannelFilter copy() {
            return new NotificationChannelFilter(this);
        }
    }

    /**
     * Class for filtering NotificationStatus
     */
    public static class NotificationStatusFilter extends Filter<NotificationStatus> {

        public NotificationStatusFilter() {}

        public NotificationStatusFilter(NotificationStatusFilter filter) {
            super(filter);
        }

        @Override
        public NotificationStatusFilter copy() {
            return new NotificationStatusFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter title;

    private StringFilter message;

    private NotificationTypeFilter type;

    private NotificationChannelFilter channel;

    private NotificationStatusFilter status;

    private BooleanFilter isRead;

    private StringFilter deepLink;

    private InstantFilter createdAt;

    private InstantFilter sentAt;

    private LongFilter userId;

    private LongFilter transactionId;

    private Boolean distinct;

    public NotificationCriteria() {}

    public NotificationCriteria(NotificationCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.title = other.optionalTitle().map(StringFilter::copy).orElse(null);
        this.message = other.optionalMessage().map(StringFilter::copy).orElse(null);
        this.type = other.optionalType().map(NotificationTypeFilter::copy).orElse(null);
        this.channel = other.optionalChannel().map(NotificationChannelFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(NotificationStatusFilter::copy).orElse(null);
        this.isRead = other.optionalIsRead().map(BooleanFilter::copy).orElse(null);
        this.deepLink = other.optionalDeepLink().map(StringFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.sentAt = other.optionalSentAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.transactionId = other.optionalTransactionId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public NotificationCriteria copy() {
        return new NotificationCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getTitle() {
        return title;
    }

    public Optional<StringFilter> optionalTitle() {
        return Optional.ofNullable(title);
    }

    public StringFilter title() {
        if (title == null) {
            setTitle(new StringFilter());
        }
        return title;
    }

    public void setTitle(StringFilter title) {
        this.title = title;
    }

    public StringFilter getMessage() {
        return message;
    }

    public Optional<StringFilter> optionalMessage() {
        return Optional.ofNullable(message);
    }

    public StringFilter message() {
        if (message == null) {
            setMessage(new StringFilter());
        }
        return message;
    }

    public void setMessage(StringFilter message) {
        this.message = message;
    }

    public NotificationTypeFilter getType() {
        return type;
    }

    public Optional<NotificationTypeFilter> optionalType() {
        return Optional.ofNullable(type);
    }

    public NotificationTypeFilter type() {
        if (type == null) {
            setType(new NotificationTypeFilter());
        }
        return type;
    }

    public void setType(NotificationTypeFilter type) {
        this.type = type;
    }

    public NotificationChannelFilter getChannel() {
        return channel;
    }

    public Optional<NotificationChannelFilter> optionalChannel() {
        return Optional.ofNullable(channel);
    }

    public NotificationChannelFilter channel() {
        if (channel == null) {
            setChannel(new NotificationChannelFilter());
        }
        return channel;
    }

    public void setChannel(NotificationChannelFilter channel) {
        this.channel = channel;
    }

    public NotificationStatusFilter getStatus() {
        return status;
    }

    public Optional<NotificationStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public NotificationStatusFilter status() {
        if (status == null) {
            setStatus(new NotificationStatusFilter());
        }
        return status;
    }

    public void setStatus(NotificationStatusFilter status) {
        this.status = status;
    }

    public BooleanFilter getIsRead() {
        return isRead;
    }

    public Optional<BooleanFilter> optionalIsRead() {
        return Optional.ofNullable(isRead);
    }

    public BooleanFilter isRead() {
        if (isRead == null) {
            setIsRead(new BooleanFilter());
        }
        return isRead;
    }

    public void setIsRead(BooleanFilter isRead) {
        this.isRead = isRead;
    }

    public StringFilter getDeepLink() {
        return deepLink;
    }

    public Optional<StringFilter> optionalDeepLink() {
        return Optional.ofNullable(deepLink);
    }

    public StringFilter deepLink() {
        if (deepLink == null) {
            setDeepLink(new StringFilter());
        }
        return deepLink;
    }

    public void setDeepLink(StringFilter deepLink) {
        this.deepLink = deepLink;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public InstantFilter getSentAt() {
        return sentAt;
    }

    public Optional<InstantFilter> optionalSentAt() {
        return Optional.ofNullable(sentAt);
    }

    public InstantFilter sentAt() {
        if (sentAt == null) {
            setSentAt(new InstantFilter());
        }
        return sentAt;
    }

    public void setSentAt(InstantFilter sentAt) {
        this.sentAt = sentAt;
    }

    public LongFilter getUserId() {
        return userId;
    }

    public Optional<LongFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public LongFilter userId() {
        if (userId == null) {
            setUserId(new LongFilter());
        }
        return userId;
    }

    public void setUserId(LongFilter userId) {
        this.userId = userId;
    }

    public LongFilter getTransactionId() {
        return transactionId;
    }

    public Optional<LongFilter> optionalTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    public LongFilter transactionId() {
        if (transactionId == null) {
            setTransactionId(new LongFilter());
        }
        return transactionId;
    }

    public void setTransactionId(LongFilter transactionId) {
        this.transactionId = transactionId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NotificationCriteria that = (NotificationCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(title, that.title) &&
            Objects.equals(message, that.message) &&
            Objects.equals(type, that.type) &&
            Objects.equals(channel, that.channel) &&
            Objects.equals(status, that.status) &&
            Objects.equals(isRead, that.isRead) &&
            Objects.equals(deepLink, that.deepLink) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(sentAt, that.sentAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(transactionId, that.transactionId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            title,
            message,
            type,
            channel,
            status,
            isRead,
            deepLink,
            createdAt,
            sentAt,
            userId,
            transactionId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "NotificationCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalTitle().map(f -> "title=" + f + ", ").orElse("") +
            optionalMessage().map(f -> "message=" + f + ", ").orElse("") +
            optionalType().map(f -> "type=" + f + ", ").orElse("") +
            optionalChannel().map(f -> "channel=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalIsRead().map(f -> "isRead=" + f + ", ").orElse("") +
            optionalDeepLink().map(f -> "deepLink=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalSentAt().map(f -> "sentAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalTransactionId().map(f -> "transactionId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
