package com.zekodnix.zaramoney.web.rest.vm;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * View Model object for storing a user's credentials.
 */
public class LoginVM {

    @NotNull
    @Size(min = 1, max = 50)
    private String username;

    @NotNull
    @Size(min = 4, max = 100)
    private String password;

    /** Ignored since item 13: sessions last via refresh tokens, not long-lived access tokens. */
    private boolean rememberMe;

    // --- Device binding (all required together for a DEVICE session; omit all for ADMIN / LEGACY) ---

    /** UUID generated once per install and kept in SecureStore. */
    @Size(min = 8, max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String deviceId;

    @Size(max = 100)
    private String deviceName;

    @Size(max = 20)
    private String platform;

    /** Base64 X.509 SubjectPublicKeyInfo of the EC P-256 device key (no user presence). */
    @Size(max = 512)
    private String devicePublicKey;

    /** Base64 X.509 SubjectPublicKeyInfo of the EC P-256 transaction key (biometric-bound). */
    @Size(max = 512)
    private String transactionPublicKey;

    /** From GET /api/auth/challenge. */
    @Size(max = 64)
    private String nonce;

    /** Device-key signature of "login\n" + nonce + "\n" + deviceId + "\n" + username. */
    @Size(max = 256)
    private String signature;

    public boolean hasDeviceBinding() {
        return deviceId != null || devicePublicKey != null || signature != null;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    public String getTransactionPublicKey() {
        return transactionPublicKey;
    }

    public void setTransactionPublicKey(String transactionPublicKey) {
        this.transactionPublicKey = transactionPublicKey;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LoginVM{" +
            "username='" + username + '\'' +
            ", rememberMe=" + rememberMe +
            ", deviceId='" + deviceId + '\'' +
            '}';
    }
}
