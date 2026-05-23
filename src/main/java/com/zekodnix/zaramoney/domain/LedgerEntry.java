package com.zekodnix.zaramoney.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A LedgerEntry.
 */
@Entity
@Table(name = "ledger_entry")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "user", "transactionLimit" }, allowSetters = true)
    private BankAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "sender", "receiver" }, allowSetters = true)
    private TransactionRecord transaction;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public LedgerEntry id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public LedgerEntry amount(BigDecimal amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return this.currency;
    }

    public LedgerEntry currency(Currency currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public EntryType getEntryType() {
        return this.entryType;
    }

    public LedgerEntry entryType(EntryType entryType) {
        this.setEntryType(entryType);
        return this;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public LedgerEntry createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BankAccount getAccount() {
        return this.account;
    }

    public void setAccount(BankAccount bankAccount) {
        this.account = bankAccount;
    }

    public LedgerEntry account(BankAccount bankAccount) {
        this.setAccount(bankAccount);
        return this;
    }

    public TransactionRecord getTransaction() {
        return this.transaction;
    }

    public void setTransaction(TransactionRecord transactionRecord) {
        this.transaction = transactionRecord;
    }

    public LedgerEntry transaction(TransactionRecord transactionRecord) {
        this.setTransaction(transactionRecord);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntry)) {
            return false;
        }
        return getId() != null && getId().equals(((LedgerEntry) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LedgerEntry{" +
            "id=" + getId() +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", entryType='" + getEntryType() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
