package com.zekodnix.zaramoney.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A IdempotencyRecord.
 */
@Entity
@Table(name = "idempotency_record")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IdempotencyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 128)
    @Column(name = "key_hash", length = 128, nullable = false, unique = true)
    private String keyHash;

    @NotNull
    @Size(max = 200)
    @Column(name = "endpoint", length = 200, nullable = false)
    private String endpoint;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Size(max = 64)
    @Column(name = "transaction_reference", length = 64)
    private String transactionReference;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public IdempotencyRecord id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyHash() {
        return this.keyHash;
    }

    public IdempotencyRecord keyHash(String keyHash) {
        this.setKeyHash(keyHash);
        return this;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public IdempotencyRecord endpoint(String endpoint) {
        this.setEndpoint(endpoint);
        return this;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Long getUserId() {
        return this.userId;
    }

    public IdempotencyRecord userId(Long userId) {
        this.setUserId(userId);
        return this;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public IdempotencyRecord createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getResponseBody() {
        return this.responseBody;
    }

    public IdempotencyRecord responseBody(String responseBody) {
        this.setResponseBody(responseBody);
        return this;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getResponseStatus() {
        return this.responseStatus;
    }

    public IdempotencyRecord responseStatus(Integer responseStatus) {
        this.setResponseStatus(responseStatus);
        return this;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getTransactionReference() {
        return this.transactionReference;
    }

    public IdempotencyRecord transactionReference(String transactionReference) {
        this.setTransactionReference(transactionReference);
        return this;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyRecord)) {
            return false;
        }
        return getId() != null && getId().equals(((IdempotencyRecord) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IdempotencyRecord{" +
            "id=" + getId() +
            ", keyHash='" + getKeyHash() + "'" +
            ", endpoint='" + getEndpoint() + "'" +
            ", userId=" + getUserId() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", responseBody='" + getResponseBody() + "'" +
            ", responseStatus=" + getResponseStatus() +
            ", transactionReference='" + getTransactionReference() + "'" +
            "}";
    }
}
