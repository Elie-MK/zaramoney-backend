package com.zekodnix.zaramoney.service.criteria;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.zekodnix.zaramoney.domain.UserDetailsAccount} entity. This class is used
 * in {@link com.zekodnix.zaramoney.web.rest.UserDetailsAccountResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /user-details-accounts?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserDetailsAccountCriteria implements Serializable, Criteria {

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter phoneNumber;

    private StringFilter facePicture;

    private StringFilter idCardPicture;

    private StringFilter country;

    private StringFilter address;

    private BooleanFilter isAgent;

    private StringFilter accountNumber;

    private BigDecimalFilter accountBalance;

    private LongFilter userLoginId;

    private Boolean distinct;

    public UserDetailsAccountCriteria() {}

    public UserDetailsAccountCriteria(UserDetailsAccountCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.phoneNumber = other.optionalPhoneNumber().map(StringFilter::copy).orElse(null);
        this.facePicture = other.optionalFacePicture().map(StringFilter::copy).orElse(null);
        this.idCardPicture = other.optionalIdCardPicture().map(StringFilter::copy).orElse(null);
        this.country = other.optionalCountry().map(StringFilter::copy).orElse(null);
        this.address = other.optionalAddress().map(StringFilter::copy).orElse(null);
        this.isAgent = other.optionalIsAgent().map(BooleanFilter::copy).orElse(null);
        this.accountNumber = other.optionalAccountNumber().map(StringFilter::copy).orElse(null);
        this.accountBalance = other.optionalAccountBalance().map(BigDecimalFilter::copy).orElse(null);
        this.userLoginId = other.optionalUserLoginId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public UserDetailsAccountCriteria copy() {
        return new UserDetailsAccountCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getPhoneNumber() {
        return phoneNumber;
    }

    public Optional<StringFilter> optionalPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    public StringFilter phoneNumber() {
        if (phoneNumber == null) {
            setPhoneNumber(new StringFilter());
        }
        return phoneNumber;
    }

    public void setPhoneNumber(StringFilter phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public StringFilter getFacePicture() {
        return facePicture;
    }

    public Optional<StringFilter> optionalFacePicture() {
        return Optional.ofNullable(facePicture);
    }

    public StringFilter facePicture() {
        if (facePicture == null) {
            setFacePicture(new StringFilter());
        }
        return facePicture;
    }

    public void setFacePicture(StringFilter facePicture) {
        this.facePicture = facePicture;
    }

    public StringFilter getIdCardPicture() {
        return idCardPicture;
    }

    public Optional<StringFilter> optionalIdCardPicture() {
        return Optional.ofNullable(idCardPicture);
    }

    public StringFilter idCardPicture() {
        if (idCardPicture == null) {
            setIdCardPicture(new StringFilter());
        }
        return idCardPicture;
    }

    public void setIdCardPicture(StringFilter idCardPicture) {
        this.idCardPicture = idCardPicture;
    }

    public StringFilter getCountry() {
        return country;
    }

    public Optional<StringFilter> optionalCountry() {
        return Optional.ofNullable(country);
    }

    public StringFilter country() {
        if (country == null) {
            setCountry(new StringFilter());
        }
        return country;
    }

    public void setCountry(StringFilter country) {
        this.country = country;
    }

    public StringFilter getAddress() {
        return address;
    }

    public Optional<StringFilter> optionalAddress() {
        return Optional.ofNullable(address);
    }

    public StringFilter address() {
        if (address == null) {
            setAddress(new StringFilter());
        }
        return address;
    }

    public void setAddress(StringFilter address) {
        this.address = address;
    }

    public BooleanFilter getIsAgent() {
        return isAgent;
    }

    public Optional<BooleanFilter> optionalIsAgent() {
        return Optional.ofNullable(isAgent);
    }

    public BooleanFilter isAgent() {
        if (isAgent == null) {
            setIsAgent(new BooleanFilter());
        }
        return isAgent;
    }

    public void setIsAgent(BooleanFilter isAgent) {
        this.isAgent = isAgent;
    }

    public StringFilter getAccountNumber() {
        return accountNumber;
    }

    public Optional<StringFilter> optionalAccountNumber() {
        return Optional.ofNullable(accountNumber);
    }

    public StringFilter accountNumber() {
        if (accountNumber == null) {
            setAccountNumber(new StringFilter());
        }
        return accountNumber;
    }

    public void setAccountNumber(StringFilter accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimalFilter getAccountBalance() {
        return accountBalance;
    }

    public Optional<BigDecimalFilter> optionalAccountBalance() {
        return Optional.ofNullable(accountBalance);
    }

    public BigDecimalFilter accountBalance() {
        if (accountBalance == null) {
            setAccountBalance(new BigDecimalFilter());
        }
        return accountBalance;
    }

    public void setAccountBalance(BigDecimalFilter accountBalance) {
        this.accountBalance = accountBalance;
    }

    public LongFilter getUserLoginId() {
        return userLoginId;
    }

    public Optional<LongFilter> optionalUserLoginId() {
        return Optional.ofNullable(userLoginId);
    }

    public LongFilter userLoginId() {
        if (userLoginId == null) {
            setUserLoginId(new LongFilter());
        }
        return userLoginId;
    }

    public void setUserLoginId(LongFilter userLoginId) {
        this.userLoginId = userLoginId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserDetailsAccountCriteria that = (UserDetailsAccountCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(phoneNumber, that.phoneNumber) &&
            Objects.equals(facePicture, that.facePicture) &&
            Objects.equals(idCardPicture, that.idCardPicture) &&
            Objects.equals(country, that.country) &&
            Objects.equals(address, that.address) &&
            Objects.equals(isAgent, that.isAgent) &&
            Objects.equals(accountNumber, that.accountNumber) &&
            Objects.equals(accountBalance, that.accountBalance) &&
            Objects.equals(userLoginId, that.userLoginId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            phoneNumber,
            facePicture,
            idCardPicture,
            country,
            address,
            isAgent,
            accountNumber,
            accountBalance,
            userLoginId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserDetailsAccountCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalPhoneNumber().map(f -> "phoneNumber=" + f + ", ").orElse("") +
            optionalFacePicture().map(f -> "facePicture=" + f + ", ").orElse("") +
            optionalIdCardPicture().map(f -> "idCardPicture=" + f + ", ").orElse("") +
            optionalCountry().map(f -> "country=" + f + ", ").orElse("") +
            optionalAddress().map(f -> "address=" + f + ", ").orElse("") +
            optionalIsAgent().map(f -> "isAgent=" + f + ", ").orElse("") +
            optionalAccountNumber().map(f -> "accountNumber=" + f + ", ").orElse("") +
            optionalAccountBalance().map(f -> "accountBalance=" + f + ", ").orElse("") +
            optionalUserLoginId().map(f -> "userLoginId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
