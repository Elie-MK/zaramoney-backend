package com.zekodnix.zaramoney.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A TransactionLimit.
 */
@Entity
@Table(name = "transaction_limit")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionLimit implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "daily_limit", precision = 21, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "monthly_limit", precision = 21, scale = 2)
    private BigDecimal monthlyLimit;

    @JsonIgnoreProperties(value = { "user", "transactionLimit" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private BankAccount account;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public TransactionLimit id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getDailyLimit() {
        return this.dailyLimit;
    }

    public TransactionLimit dailyLimit(BigDecimal dailyLimit) {
        this.setDailyLimit(dailyLimit);
        return this;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getMonthlyLimit() {
        return this.monthlyLimit;
    }

    public TransactionLimit monthlyLimit(BigDecimal monthlyLimit) {
        this.setMonthlyLimit(monthlyLimit);
        return this;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public BankAccount getAccount() {
        return this.account;
    }

    public void setAccount(BankAccount bankAccount) {
        this.account = bankAccount;
    }

    public TransactionLimit account(BankAccount bankAccount) {
        this.setAccount(bankAccount);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionLimit)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionLimit) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionLimit{" +
            "id=" + getId() +
            ", dailyLimit=" + getDailyLimit() +
            ", monthlyLimit=" + getMonthlyLimit() +
            "}";
    }
}
