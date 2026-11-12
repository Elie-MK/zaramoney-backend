package com.zekodnix.zaramoney.web.rest.vm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PUT /api/plus/security/pin}. */
public class SetPinVM {

    /** The account (login) password, required to set or change the PIN. */
    @NotBlank
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{4,6}")
    private String pin;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
