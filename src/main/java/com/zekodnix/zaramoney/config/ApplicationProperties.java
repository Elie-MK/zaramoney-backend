package com.zekodnix.zaramoney.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Zaramoneybackend.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    private final Cloudinary cloudinary = new Cloudinary();

    public Cloudinary getCloudinary() {
        return cloudinary;
    }

    public static class Cloudinary {

        private String cloudname;
        private String apikey;
        private String apisecret;

        public String getCloudname() {
            return cloudname;
        }

        public void setCloudname(String cloudname) {
            this.cloudname = cloudname;
        }

        public String getApikey() {
            return apikey;
        }

        public void setApikey(String apikey) {
            this.apikey = apikey;
        }

        public String getApisecret() {
            return apisecret;
        }

        public void setApisecret(String apisecret) {
            this.apisecret = apisecret;
        }
    }
    private final Security security = new Security();

    public Security getSecurity() {
        return security;
    }

    public static class Security {

        /** In-memory rate limiting of sensitive endpoints and login lockout. Disabled in the test config only. */
        private boolean rateLimitEnabled = true;

        public boolean isRateLimitEnabled() {
            return rateLimitEnabled;
        }

        public void setRateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
        }

        private final Sessions sessions = new Sessions();

        public Sessions getSessions() {
            return sessions;
        }
    }

    /** Sessions, refresh tokens and device binding (TODO.md item 13). Prefix: application.security.sessions */
    public static class Sessions {

        public enum DeviceBindingMode {
            /** Old app versions (no device key) can still log in with a LEGACY session. Use during the rollout. */
            OPTIONAL,
            /** Every non-admin session must be device-bound (signed requests). */
            ENFORCE,
        }

        private DeviceBindingMode deviceBinding = DeviceBindingMode.OPTIONAL;
        /** Access token lifetime for DEVICE and ADMIN sessions. */
        private long accessTokenValiditySeconds = 900;
        /** Access token lifetime for LEGACY sessions (old app, no refresh token). */
        private long legacyTokenValiditySeconds = 86400;
        /** Each refresh token is valid this long (renewed on every refresh). */
        private long refreshTokenValidityDays = 30;
        /** Absolute session lifetime: after this, a password login is required. */
        private long sessionMaxLifetimeDays = 90;
        /** Lifetime of an ADMIN session (no refresh token: the admin logs in again). */
        private long adminSessionValiditySeconds = 900;
        /** Max active DEVICE sessions per user; the oldest is revoked beyond this. */
        private int maxDeviceSessions = 5;
        /** Accepted clock difference for signed requests. */
        private long signatureWindowSeconds = 60;
        /** A rotated refresh token presented again within this delay is treated as a parallel request, not as theft. */
        private long refreshReuseGraceSeconds = 30;

        public DeviceBindingMode getDeviceBinding() {
            return deviceBinding;
        }

        public void setDeviceBinding(DeviceBindingMode deviceBinding) {
            this.deviceBinding = deviceBinding;
        }

        public boolean isEnforced() {
            return deviceBinding == DeviceBindingMode.ENFORCE;
        }

        public long getAccessTokenValiditySeconds() {
            return accessTokenValiditySeconds;
        }

        public void setAccessTokenValiditySeconds(long accessTokenValiditySeconds) {
            this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        }

        public long getLegacyTokenValiditySeconds() {
            return legacyTokenValiditySeconds;
        }

        public void setLegacyTokenValiditySeconds(long legacyTokenValiditySeconds) {
            this.legacyTokenValiditySeconds = legacyTokenValiditySeconds;
        }

        public long getRefreshTokenValidityDays() {
            return refreshTokenValidityDays;
        }

        public void setRefreshTokenValidityDays(long refreshTokenValidityDays) {
            this.refreshTokenValidityDays = refreshTokenValidityDays;
        }

        public long getSessionMaxLifetimeDays() {
            return sessionMaxLifetimeDays;
        }

        public void setSessionMaxLifetimeDays(long sessionMaxLifetimeDays) {
            this.sessionMaxLifetimeDays = sessionMaxLifetimeDays;
        }

        public long getAdminSessionValiditySeconds() {
            return adminSessionValiditySeconds;
        }

        public void setAdminSessionValiditySeconds(long adminSessionValiditySeconds) {
            this.adminSessionValiditySeconds = adminSessionValiditySeconds;
        }

        public int getMaxDeviceSessions() {
            return maxDeviceSessions;
        }

        public void setMaxDeviceSessions(int maxDeviceSessions) {
            this.maxDeviceSessions = maxDeviceSessions;
        }

        public long getSignatureWindowSeconds() {
            return signatureWindowSeconds;
        }

        public void setSignatureWindowSeconds(long signatureWindowSeconds) {
            this.signatureWindowSeconds = signatureWindowSeconds;
        }

        public long getRefreshReuseGraceSeconds() {
            return refreshReuseGraceSeconds;
        }

        public void setRefreshReuseGraceSeconds(long refreshReuseGraceSeconds) {
            this.refreshReuseGraceSeconds = refreshReuseGraceSeconds;
        }
    }

    // jhipster-needle-application-properties-property-class
}
