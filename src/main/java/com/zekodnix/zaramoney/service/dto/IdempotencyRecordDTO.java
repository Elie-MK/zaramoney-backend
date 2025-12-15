package com.zekodnix.zaramoney.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.zekodnix.zaramoney.domain.IdempotencyRecord} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IdempotencyRecordDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(max = 128)
    private String keyHash;

    @NotNull
    @Size(max = 200)
    private String endpoint;

    @NotNull
    private Long userId;

    @NotNull
    private Instant createdAt;

    @Lob
    private String responseBody;

    private Integer responseStatus;

    @Size(max = 64)
    private String transactionReference;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyRecordDTO)) {
            return false;
        }

        IdempotencyRecordDTO idempotencyRecordDTO = (IdempotencyRecordDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, idempotencyRecordDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IdempotencyRecordDTO{" +
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
