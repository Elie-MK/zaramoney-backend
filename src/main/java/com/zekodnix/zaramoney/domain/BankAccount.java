package com.zekodnix.zaramoney.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A BankAccount.
 */
@Entity
@Table(name = "bank_account")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BankAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 8, max = 16)
    @Column(name = "account_number", length = 16, nullable = false, unique = true)
    private String accountNumber;

    @NotNull
    @Column(name = "balance", precision = 21, scale = 2, nullable = false)
    private BigDecimal balance;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @JsonIgnoreProperties(value = { "account" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "account")
    private TransactionLimit transactionLimit;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public BankAccount id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return this.accountNumber;
    }

    public BankAccount accountNumber(String accountNumber) {
        this.setAccountNumber(accountNumber);
        return this;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getBalance() {
        return this.balance;
    }

    public BankAccount balance(BigDecimal balance) {
        this.setBalance(balance);
        return this;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Currency getCurrency() {
        return this.currency;
    }

    public BankAccount currency(Currency currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public BankAccount status(AccountStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public BankAccount createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BankAccount user(User user) {
        this.setUser(user);
        return this;
    }

    public TransactionLimit getTransactionLimit() {
        return this.transactionLimit;
    }

    public void setTransactionLimit(TransactionLimit transactionLimit) {
        if (this.transactionLimit != null) {
            this.transactionLimit.setAccount(null);
        }
        if (transactionLimit != null) {
            transactionLimit.setAccount(this);
        }
        this.transactionLimit = transactionLimit;
    }

    public BankAccount transactionLimit(TransactionLimit transactionLimit) {
        this.setTransactionLimit(transactionLimit);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BankAccount)) {
            return false;
        }
        return getId() != null && getId().equals(((BankAccount) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BankAccount{" +
            "id=" + getId() +
            ", accountNumber='" + getAccountNumber() + "'" +
            ", balance=" + getBalance() +
            ", currency='" + getCurrency() + "'" +
            ", status='" + getStatus() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
