package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.zekodnix.zaramoney.domain.LedgerEntry} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerEntryDTO implements Serializable {

    private Long id;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private Currency currency;

    @NotNull
    private EntryType entryType;

    @NotNull
    private Instant createdAt;

    private BankAccountDTO account;

    private TransactionRecordDTO transaction;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BankAccountDTO getAccount() {
        return account;
    }

    public void setAccount(BankAccountDTO account) {
        this.account = account;
    }

    public TransactionRecordDTO getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionRecordDTO transaction) {
        this.transaction = transaction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntryDTO)) {
            return false;
        }

        LedgerEntryDTO ledgerEntryDTO = (LedgerEntryDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, ledgerEntryDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LedgerEntryDTO{" +
            "id=" + getId() +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", entryType='" + getEntryType() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", account=" + getAccount() +
            ", transaction=" + getTransaction() +
            "}";
    }
}
