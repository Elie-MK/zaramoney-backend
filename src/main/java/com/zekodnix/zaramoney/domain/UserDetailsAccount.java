package com.zekodnix.zaramoney.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A UserDetailsAccount.
 */
@Entity
@Table(name = "user_details_account")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserDetailsAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @NotNull
    @Column(name = "face_picture", nullable = false)
    private String facePicture;

    @NotNull
    @Column(name = "id_card_picture", nullable = false)
    private String idCardPicture;

    @NotNull
    @Column(name = "country", nullable = false)
    private String country;

    @NotNull
    @Column(name = "address", nullable = false)
    private String address;

    @NotNull
    @Column(name = "is_agent", nullable = false)
    private Boolean isAgent;

    @NotNull
    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @NotNull
    @Column(name = "account_balance", precision = 21, scale = 2, nullable = false)
    private BigDecimal accountBalance;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private User userLogin;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public UserDetailsAccount id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public UserDetailsAccount phoneNumber(String phoneNumber) {
        this.setPhoneNumber(phoneNumber);
        return this;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFacePicture() {
        return this.facePicture;
    }

    public UserDetailsAccount facePicture(String facePicture) {
        this.setFacePicture(facePicture);
        return this;
    }

    public void setFacePicture(String facePicture) {
        this.facePicture = facePicture;
    }

    public String getIdCardPicture() {
        return this.idCardPicture;
    }

    public UserDetailsAccount idCardPicture(String idCardPicture) {
        this.setIdCardPicture(idCardPicture);
        return this;
    }

    public void setIdCardPicture(String idCardPicture) {
        this.idCardPicture = idCardPicture;
    }

    public String getCountry() {
        return this.country;
    }

    public UserDetailsAccount country(String country) {
        this.setCountry(country);
        return this;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAddress() {
        return this.address;
    }

    public UserDetailsAccount address(String address) {
        this.setAddress(address);
        return this;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getIsAgent() {
        return this.isAgent;
    }

    public UserDetailsAccount isAgent(Boolean isAgent) {
        this.setIsAgent(isAgent);
        return this;
    }

    public void setIsAgent(Boolean isAgent) {
        this.isAgent = isAgent;
    }

    public String getAccountNumber() {
        return this.accountNumber;
    }

    public UserDetailsAccount accountNumber(String accountNumber) {
        this.setAccountNumber(accountNumber);
        return this;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getAccountBalance() {
        return this.accountBalance;
    }

    public UserDetailsAccount accountBalance(BigDecimal accountBalance) {
        this.setAccountBalance(accountBalance);
        return this;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }

    public User getUserLogin() {
        return this.userLogin;
    }

    public void setUserLogin(User user) {
        this.userLogin = user;
    }

    public UserDetailsAccount userLogin(User user) {
        this.setUserLogin(user);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserDetailsAccount)) {
            return false;
        }
        return getId() != null && getId().equals(((UserDetailsAccount) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserDetailsAccount{" +
            "id=" + getId() +
            ", phoneNumber='" + getPhoneNumber() + "'" +
            ", facePicture='" + getFacePicture() + "'" +
            ", idCardPicture='" + getIdCardPicture() + "'" +
            ", country='" + getCountry() + "'" +
            ", address='" + getAddress() + "'" +
            ", isAgent='" + getIsAgent() + "'" +
            ", accountNumber='" + getAccountNumber() + "'" +
            ", accountBalance=" + getAccountBalance() +
            "}";
    }
}
