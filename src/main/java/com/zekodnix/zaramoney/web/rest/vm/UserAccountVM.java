package com.zekodnix.zaramoney.web.rest.vm;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

public class UserAccountVM {

    @NotBlank
    private String phoneNumber;

    private MultipartFile facePicture;

    private MultipartFile idCardPicture;

    @NotBlank
    private String country;

    @NotBlank
    private String address;

    @NotBlank
    private String expoPushToken;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public MultipartFile getFacePicture() {
        return facePicture;
    }

    public void setFacePicture(MultipartFile facePicture) {
        this.facePicture = facePicture;
    }

    public MultipartFile getIdCardPicture() {
        return idCardPicture;
    }

    public void setIdCardPicture(MultipartFile idCardPicture) {
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

    public String getExpoPushToken() {
        return expoPushToken;
    }

    public void setExpoPushToken(String expoPushToken) {
        this.expoPushToken = expoPushToken;
    }
}
