package com.zekodnix.zaramoney.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

    private final Fees fees = new Fees();

    private final Reconciliation reconciliation = new Reconciliation();

    private final Push push = new Push();

    public Push getPush() {
        return push;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    // jhipster-needle-application-properties-property

    public Fees getFees() {
        return fees;
    }

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

    /**
     * Fee schedule and balance rule ({@code application.fees}), validated at startup by {@code FeeSchedule}.
     * Each list is ordered: the first tier whose {@code up-to} is >= the amount applies (amounts have 2 decimals, so
     * {@code up-to: 100.99} means "below 101"). The last tier has no {@code up-to}. Fee = amount x rate, HALF_UP to cents.
     * Quote the values in YAML ("0.0095") so they are read as exact decimals.
     */
    /** Expo push client ({@code ExpoPushService}). */
    public static class Push {

        private String expoBaseUrl = "https://exp.host/--/api/v2";
        /** Retries after the first attempt, only for network errors, timeouts, 5xx and 429. */
        private int maxRetries = 3;
        /** First retry delay; doubles each time (with jitter). */
        private java.time.Duration initialBackoff = java.time.Duration.ofSeconds(1);
        /** Per attempt. */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(10);

        public String getExpoBaseUrl() {
            return expoBaseUrl;
        }

        public void setExpoBaseUrl(String expoBaseUrl) {
            this.expoBaseUrl = expoBaseUrl;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public java.time.Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(java.time.Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public java.time.Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(java.time.Duration timeout) {
            this.timeout = timeout;
        }
    }

    /** Daily ledger reconciliation ({@code LedgerReconciliationService}); the cron is read via its placeholder. */
    public static class Reconciliation {

        /** Spring cron, Africa/Kinshasa time. */
        private String cron = "0 15 2 * * *";

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Fees {

        /** A customer balance must stay >= this after amount + fee. */
        private BigDecimal minBalance = new BigDecimal("5.00");

        private List<Tier> transfer = new ArrayList<>(
            List.of(new Tier("100.99", "0"), new Tier("2500", "0.0095"), new Tier(null, "0.0075"))
        );

        private List<Tier> withdrawal = new ArrayList<>(
            List.of(new Tier("5", "0.013"), new Tier("20", "0.008"), new Tier("50", "0.025"), new Tier(null, "0.0199"))
        );

        public BigDecimal getMinBalance() {
            return minBalance;
        }

        public void setMinBalance(BigDecimal minBalance) {
            this.minBalance = minBalance;
        }

        public List<Tier> getTransfer() {
            return transfer;
        }

        public void setTransfer(List<Tier> transfer) {
            this.transfer = transfer;
        }

        public List<Tier> getWithdrawal() {
            return withdrawal;
        }

        public void setWithdrawal(List<Tier> withdrawal) {
            this.withdrawal = withdrawal;
        }

        public static class Tier {

            /** Inclusive upper bound of the amount; null for the last tier. */
            private BigDecimal upTo;
            private BigDecimal rate;

            public Tier() {}

            public Tier(String upTo, String rate) {
                this.upTo = upTo == null ? null : new BigDecimal(upTo);
                this.rate = new BigDecimal(rate);
            }

            public BigDecimal getUpTo() {
                return upTo;
            }

            public void setUpTo(BigDecimal upTo) {
                this.upTo = upTo;
            }

            public BigDecimal getRate() {
                return rate;
            }

            public void setRate(BigDecimal rate) {
                this.rate = rate;
            }
        }
    }
}
