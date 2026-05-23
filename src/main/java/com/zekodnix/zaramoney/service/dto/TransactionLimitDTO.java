package com.zekodnix.zaramoney.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A DTO for the {@link com.zekodnix.zaramoney.domain.TransactionLimit} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionLimitDTO implements Serializable {

    private Long id;

    private BigDecimal dailyLimit;

    private BigDecimal monthlyLimit;

    private BankAccountDTO account;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public BankAccountDTO getAccount() {
        return account;
    }

    public void setAccount(BankAccountDTO account) {
        this.account = account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionLimitDTO)) {
            return false;
        }

        TransactionLimitDTO transactionLimitDTO = (TransactionLimitDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionLimitDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionLimitDTO{" +
            "id=" + getId() +
            ", dailyLimit=" + getDailyLimit() +
            ", monthlyLimit=" + getMonthlyLimit() +
            ", account=" + getAccount() +
            "}";
    }
}
