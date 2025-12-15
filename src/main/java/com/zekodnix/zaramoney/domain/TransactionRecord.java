package com.zekodnix.zaramoney.domain;

import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A TransactionRecord.
 */
@Entity
@Table(name = "transaction_record")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @NotNull
    @DecimalMin(value = "1")
    @Column(name = "send_amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal sendAmount;

    @NotNull
    @DecimalMin(value = "1")
    @Column(name = "receive_amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal receiveAmount;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Size(max = 255)
    @Column(name = "description", length = 255)
    private String description;

    @NotNull
    @Size(min = 8, max = 16)
    @Column(name = "sender_account_number", length = 16, nullable = false)
    private String senderAccountNumber;

    @NotNull
    @Size(min = 8, max = 16)
    @Column(name = "receiver_account_number", length = 16, nullable = false)
    private String receiverAccountNumber;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(name = "currency_send_amount", length = 3, nullable = false)
    private String currencySendAmount;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(name = "currency_receive_amount", length = 3, nullable = false)
    private String currencyReceiveAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    private TransactionStatus transactionStatus;

    @NotNull
    @Size(min = 10, max = 64)
    @Column(name = "transaction_reference", length = 64, nullable = false, unique = true)
    private String transactionReference;

    @NotNull
    @Min(value = 0)
    @Max(value = 100)
    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_status", nullable = false)
    private FraudStatus fraudStatus;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private User userLogin;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public TransactionRecord id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionType getTransactionType() {
        return this.transactionType;
    }

    public TransactionRecord transactionType(TransactionType transactionType) {
        this.setTransactionType(transactionType);
        return this;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getSendAmount() {
        return this.sendAmount;
    }

    public TransactionRecord sendAmount(BigDecimal sendAmount) {
        this.setSendAmount(sendAmount);
        return this;
    }

    public void setSendAmount(BigDecimal sendAmount) {
        this.sendAmount = sendAmount;
    }

    public BigDecimal getReceiveAmount() {
        return this.receiveAmount;
    }

    public TransactionRecord receiveAmount(BigDecimal receiveAmount) {
        this.setReceiveAmount(receiveAmount);
        return this;
    }

    public void setReceiveAmount(BigDecimal receiveAmount) {
        this.receiveAmount = receiveAmount;
    }

    public Instant getTransactionDate() {
        return this.transactionDate;
    }

    public TransactionRecord transactionDate(Instant transactionDate) {
        this.setTransactionDate(transactionDate);
        return this;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return this.description;
    }

    public TransactionRecord description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSenderAccountNumber() {
        return this.senderAccountNumber;
    }

    public TransactionRecord senderAccountNumber(String senderAccountNumber) {
        this.setSenderAccountNumber(senderAccountNumber);
        return this;
    }

    public void setSenderAccountNumber(String senderAccountNumber) {
        this.senderAccountNumber = senderAccountNumber;
    }

    public String getReceiverAccountNumber() {
        return this.receiverAccountNumber;
    }

    public TransactionRecord receiverAccountNumber(String receiverAccountNumber) {
        this.setReceiverAccountNumber(receiverAccountNumber);
        return this;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
    }

    public String getCurrencySendAmount() {
        return this.currencySendAmount;
    }

    public TransactionRecord currencySendAmount(String currencySendAmount) {
        this.setCurrencySendAmount(currencySendAmount);
        return this;
    }

    public void setCurrencySendAmount(String currencySendAmount) {
        this.currencySendAmount = currencySendAmount;
    }

    public String getCurrencyReceiveAmount() {
        return this.currencyReceiveAmount;
    }

    public TransactionRecord currencyReceiveAmount(String currencyReceiveAmount) {
        this.setCurrencyReceiveAmount(currencyReceiveAmount);
        return this;
    }

    public void setCurrencyReceiveAmount(String currencyReceiveAmount) {
        this.currencyReceiveAmount = currencyReceiveAmount;
    }

    public TransactionStatus getTransactionStatus() {
        return this.transactionStatus;
    }

    public TransactionRecord transactionStatus(TransactionStatus transactionStatus) {
        this.setTransactionStatus(transactionStatus);
        return this;
    }

    public void setTransactionStatus(TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public String getTransactionReference() {
        return this.transactionReference;
    }

    public TransactionRecord transactionReference(String transactionReference) {
        this.setTransactionReference(transactionReference);
        return this;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public Integer getRiskScore() {
        return this.riskScore;
    }

    public TransactionRecord riskScore(Integer riskScore) {
        this.setRiskScore(riskScore);
        return this;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public FraudStatus getFraudStatus() {
        return this.fraudStatus;
    }

    public TransactionRecord fraudStatus(FraudStatus fraudStatus) {
        this.setFraudStatus(fraudStatus);
        return this;
    }

    public void setFraudStatus(FraudStatus fraudStatus) {
        this.fraudStatus = fraudStatus;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public TransactionRecord createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public TransactionRecord updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUserLogin() {
        return this.userLogin;
    }

    public void setUserLogin(User user) {
        this.userLogin = user;
    }

    public TransactionRecord userLogin(User user) {
        this.setUserLogin(user);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRecord)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionRecord) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRecord{" +
            "id=" + getId() +
            ", transactionType='" + getTransactionType() + "'" +
            ", sendAmount=" + getSendAmount() +
            ", receiveAmount=" + getReceiveAmount() +
            ", transactionDate='" + getTransactionDate() + "'" +
            ", description='" + getDescription() + "'" +
            ", senderAccountNumber='" + getSenderAccountNumber() + "'" +
            ", receiverAccountNumber='" + getReceiverAccountNumber() + "'" +
            ", currencySendAmount='" + getCurrencySendAmount() + "'" +
            ", currencyReceiveAmount='" + getCurrencyReceiveAmount() + "'" +
            ", transactionStatus='" + getTransactionStatus() + "'" +
            ", transactionReference='" + getTransactionReference() + "'" +
            ", riskScore=" + getRiskScore() +
            ", fraudStatus='" + getFraudStatus() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
