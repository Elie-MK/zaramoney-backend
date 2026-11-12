package com.zekodnix.zaramoney.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of {@code POST /api/admin/backoffice/user/deposit}: cash received by ZaraMoney and credited to a customer wallet. */
public class DepositDto {

    /** Strictly positive, at most 2 decimals. */
    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 19, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 8, max = 16)
    private String userAccountNumber;

    /** Login (e-mail) of the account owner; must match the account, as a guard against typos in the account number. */
    @NotBlank
    @Size(max = 254)
    private String userEmail;

    /**
     * One key per deposit (a UUID). Retrying with the same key returns the original deposit instead of crediting twice;
     * the same key with a different amount or account is rejected (409 idempotencykeyreused).
     */
    @NotBlank
    @Size(min = 8, max = 128)
    private String idempotencyKey;

    public DepositDto() {}

    public DepositDto(BigDecimal amount, String userAccountNumber, String userEmail, String idempotencyKey) {
        this.amount = amount;
        this.userAccountNumber = userAccountNumber;
        this.userEmail = userEmail;
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getUserAccountNumber() {
        return userAccountNumber;
    }

    public void setUserAccountNumber(String userAccountNumber) {
        this.userAccountNumber = userAccountNumber;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
