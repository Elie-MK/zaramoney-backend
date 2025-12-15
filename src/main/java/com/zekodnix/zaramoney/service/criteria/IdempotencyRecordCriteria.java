package com.zekodnix.zaramoney.service.criteria;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.IdempotencyRecord} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.IdempotencyRecordResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /idempotency-records?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IdempotencyRecordCriteria implements Serializable, Criteria {

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter keyHash;

    private StringFilter endpoint;

    private LongFilter userId;

    private InstantFilter createdAt;

    private IntegerFilter responseStatus;

    private StringFilter transactionReference;

    private Boolean distinct;

    public IdempotencyRecordCriteria() {}

    public IdempotencyRecordCriteria(IdempotencyRecordCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.keyHash = other.optionalKeyHash().map(StringFilter::copy).orElse(null);
        this.endpoint = other.optionalEndpoint().map(StringFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.responseStatus = other.optionalResponseStatus().map(IntegerFilter::copy).orElse(null);
        this.transactionReference = other.optionalTransactionReference().map(StringFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public IdempotencyRecordCriteria copy() {
        return new IdempotencyRecordCriteria(this);
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

    public StringFilter getKeyHash() {
        return keyHash;
    }

    public Optional<StringFilter> optionalKeyHash() {
        return Optional.ofNullable(keyHash);
    }

    public StringFilter keyHash() {
        if (keyHash == null) {
            setKeyHash(new StringFilter());
        }
        return keyHash;
    }

    public void setKeyHash(StringFilter keyHash) {
        this.keyHash = keyHash;
    }

    public StringFilter getEndpoint() {
        return endpoint;
    }

    public Optional<StringFilter> optionalEndpoint() {
        return Optional.ofNullable(endpoint);
    }

    public StringFilter endpoint() {
        if (endpoint == null) {
            setEndpoint(new StringFilter());
        }
        return endpoint;
    }

    public void setEndpoint(StringFilter endpoint) {
        this.endpoint = endpoint;
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

    public IntegerFilter getResponseStatus() {
        return responseStatus;
    }

    public Optional<IntegerFilter> optionalResponseStatus() {
        return Optional.ofNullable(responseStatus);
    }

    public IntegerFilter responseStatus() {
        if (responseStatus == null) {
            setResponseStatus(new IntegerFilter());
        }
        return responseStatus;
    }

    public void setResponseStatus(IntegerFilter responseStatus) {
        this.responseStatus = responseStatus;
    }

    public StringFilter getTransactionReference() {
        return transactionReference;
    }

    public Optional<StringFilter> optionalTransactionReference() {
        return Optional.ofNullable(transactionReference);
    }

    public StringFilter transactionReference() {
        if (transactionReference == null) {
            setTransactionReference(new StringFilter());
        }
        return transactionReference;
    }

    public void setTransactionReference(StringFilter transactionReference) {
        this.transactionReference = transactionReference;
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
        final IdempotencyRecordCriteria that = (IdempotencyRecordCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(keyHash, that.keyHash) &&
            Objects.equals(endpoint, that.endpoint) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(responseStatus, that.responseStatus) &&
            Objects.equals(transactionReference, that.transactionReference) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, keyHash, endpoint, userId, createdAt, responseStatus, transactionReference, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IdempotencyRecordCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalKeyHash().map(f -> "keyHash=" + f + ", ").orElse("") +
            optionalEndpoint().map(f -> "endpoint=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalResponseStatus().map(f -> "responseStatus=" + f + ", ").orElse("") +
            optionalTransactionReference().map(f -> "transactionReference=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
