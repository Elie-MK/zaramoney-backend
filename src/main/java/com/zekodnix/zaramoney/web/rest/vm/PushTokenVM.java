package com.zekodnix.zaramoney.web.rest.vm;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/plus/push-token}. {@code null} clears the token (logout / notifications turned off). */
public class PushTokenVM {

    @Size(max = 255)
    @Pattern(regexp = "^(ExponentPushToken|ExpoPushToken)\\[[^\\]\\s]{1,200}\\]$", message = "not an Expo push token")
    private String expoPushToken;

    public String getExpoPushToken() {
        return expoPushToken;
    }

    public void setExpoPushToken(String expoPushToken) {
        this.expoPushToken = expoPushToken;
    }
}
