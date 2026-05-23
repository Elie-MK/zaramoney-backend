package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.zekodnix.zaramoney.domain.UserDetailsAccount} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserDetailsAccountDTO implements Serializable {

    private Long id;

    @NotNull
    private String phoneNumber;

    @NotNull
    private String facePicture;

    @NotNull
    private String idCardPicture;

    @NotNull
    private String country;

    @NotNull
    private String address;

    @NotNull
    private Boolean isAgent;

    @NotNull
    private KycStatus kycStatus;

    private UserDTO user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFacePicture() {
        return facePicture;
    }

    public void setFacePicture(String facePicture) {
        this.facePicture = facePicture;
    }

    public String getIdCardPicture() {
        return idCardPicture;
    }

    public void setIdCardPicture(String idCardPicture) {
        this.idCardPicture = idCardPicture;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getIsAgent() {
        return isAgent;
    }

    public void setIsAgent(Boolean isAgent) {
        this.isAgent = isAgent;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserDetailsAccountDTO)) {
            return false;
        }

        UserDetailsAccountDTO userDetailsAccountDTO = (UserDetailsAccountDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, userDetailsAccountDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserDetailsAccountDTO{" +
            "id=" + getId() +
            ", phoneNumber='" + getPhoneNumber() + "'" +
            ", facePicture='" + getFacePicture() + "'" +
            ", idCardPicture='" + getIdCardPicture() + "'" +
            ", country='" + getCountry() + "'" +
            ", address='" + getAddress() + "'" +
            ", isAgent='" + getIsAgent() + "'" +
            ", kycStatus='" + getKycStatus() + "'" +
            ", user=" + getUser() +
            "}";
    }
}
