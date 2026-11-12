package com.zekodnix.zaramoney.domain;

import com.zekodnix.zaramoney.domain.enumeration.AccessBlockType;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/** A blocked user (by login) or device (by device id): login and refresh are refused. */
@Entity
@Table(name = "access_block")
public class AccessBlock implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", length = 20, nullable = false)
    private AccessBlockType blockType;

    @Column(name = "block_value", length = 100, nullable = false)
    private String blockValue;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public AccessBlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(AccessBlockType blockType) {
        this.blockType = blockType;
    }

    public String getBlockValue() {
        return blockValue;
    }

    public void setBlockValue(String blockValue) {
        this.blockValue = blockValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AccessBlock other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
