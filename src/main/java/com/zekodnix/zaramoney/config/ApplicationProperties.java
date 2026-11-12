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

    private final Fraud fraud = new Fraud();

    public Fraud getFraud() {
        return fraud;
    }

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
    /**
     * Fraud scoring ({@code FraudScoringService}). Score 0–100 = sum of the rule points that fired (capped).
     * {@code suspicious-score} ≤ score < {@code block-score} → SUSPICIOUS (allowed, flagged); score ≥ {@code block-score}
     * or a hard rule → BLOCKED (rejected only in {@code enforce} mode; in {@code monitor} mode it completes and is flagged).
     */
    public static class Fraud {

        public enum Mode {
            /** Score and flag, never block (rollout default: tune on real traffic first). */
            MONITOR,
            /** BLOCKED payments are rejected (403 transactionblocked). */
            ENFORCE,
        }

        private Mode mode = Mode.MONITOR;
        private int suspiciousScore = 40;
        private int blockScore = 70;
        /** Rule: more than {@code maxCount} outgoing payments within {@code window}. */
        private final Velocity burst = new Velocity(java.time.Duration.ofMinutes(10), 5, 30);
        /** Hard rule: more than {@code maxCount} outgoing payments within {@code window} → BLOCKED. */
        private final Velocity hourlyHardLimit = new Velocity(java.time.Duration.ofHours(1), 15, 0);
        /** Rule: amount + fee ≥ {@code ratio} of the balance (amount > {@code minAmount}). */
        private final Drain drain = new Drain();
        /** Rule: account younger than {@code age} (amount > {@code minAmount}). */
        private final Recent newAccount = new Recent(java.time.Duration.ofHours(24), "100", 25);
        /** Rule: device first seen less than {@code age} ago (amount > {@code minAmount}). */
        private final Recent newDevice = new Recent(java.time.Duration.ofHours(1), "100", 25);
        /** Hard rule: on a new device, more than this sent within {@code newDevice.age} (this payment included) → BLOCKED. */
        private java.math.BigDecimal newDeviceHardBlockAmount = new java.math.BigDecimal("2000");
        /** Rule: PIN set/changed less than {@code age} ago (amount > {@code minAmount}). */
        private final Recent pinChanged = new Recent(java.time.Duration.ofHours(24), "100", 20);
        /** Rule: amount > {@code multiplier} × the 30-day average (amount > {@code minAmount}, ≥ {@code minHistory} payments). */
        private final UnusualAmount unusualAmount = new UnusualAmount();
        /** Rule: ≥ {@code maxCount} different receivers within {@code window} (this one included). */
        private final Velocity fanOut = new Velocity(java.time.Duration.ofHours(24), 10, 20);
        /** Rule (transfers, receiver not an agent): receiver paid by ≥ {@code maxCount} different senders within {@code window}. */
        private final Velocity mule = new Velocity(java.time.Duration.ofHours(24), 10, 20);

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public int getSuspiciousScore() {
            return suspiciousScore;
        }

        public void setSuspiciousScore(int suspiciousScore) {
            this.suspiciousScore = suspiciousScore;
        }

        public int getBlockScore() {
            return blockScore;
        }

        public void setBlockScore(int blockScore) {
            this.blockScore = blockScore;
        }

        public Velocity getBurst() {
            return burst;
        }

        public Velocity getHourlyHardLimit() {
            return hourlyHardLimit;
        }

        public Drain getDrain() {
            return drain;
        }

        public Recent getNewAccount() {
            return newAccount;
        }

        public Recent getNewDevice() {
            return newDevice;
        }

        public java.math.BigDecimal getNewDeviceHardBlockAmount() {
            return newDeviceHardBlockAmount;
        }

        public void setNewDeviceHardBlockAmount(java.math.BigDecimal newDeviceHardBlockAmount) {
            this.newDeviceHardBlockAmount = newDeviceHardBlockAmount;
        }

        public Recent getPinChanged() {
            return pinChanged;
        }

        public UnusualAmount getUnusualAmount() {
            return unusualAmount;
        }

        public Velocity getFanOut() {
            return fanOut;
        }

        public Velocity getMule() {
            return mule;
        }

        public static class Velocity {

            private java.time.Duration window;
            private int maxCount;
            private int points;

            public Velocity() {}

            Velocity(java.time.Duration window, int maxCount, int points) {
                this.window = window;
                this.maxCount = maxCount;
                this.points = points;
            }

            public java.time.Duration getWindow() {
                return window;
            }

            public void setWindow(java.time.Duration window) {
                this.window = window;
            }

            public int getMaxCount() {
                return maxCount;
            }

            public void setMaxCount(int maxCount) {
                this.maxCount = maxCount;
            }

            public int getPoints() {
                return points;
            }

            public void setPoints(int points) {
                this.points = points;
            }
        }

        public static class Recent {

            private java.time.Duration age;
            private java.math.BigDecimal minAmount;
            private int points;

            public Recent() {}

            Recent(java.time.Duration age, String minAmount, int points) {
                this.age = age;
                this.minAmount = new java.math.BigDecimal(minAmount);
                this.points = points;
            }

            public java.time.Duration getAge() {
                return age;
            }

            public void setAge(java.time.Duration age) {
                this.age = age;
            }

            public java.math.BigDecimal getMinAmount() {
                return minAmount;
            }

            public void setMinAmount(java.math.BigDecimal minAmount) {
                this.minAmount = minAmount;
            }

            public int getPoints() {
                return points;
            }

            public void setPoints(int points) {
                this.points = points;
            }
        }

        public static class Drain {

            private java.math.BigDecimal ratio = new java.math.BigDecimal("0.90");
            private java.math.BigDecimal minAmount = new java.math.BigDecimal("50");
            private int points = 20;

            public java.math.BigDecimal getRatio() {
                return ratio;
            }

            public void setRatio(java.math.BigDecimal ratio) {
                this.ratio = ratio;
            }

            public java.math.BigDecimal getMinAmount() {
                return minAmount;
            }

            public void setMinAmount(java.math.BigDecimal minAmount) {
                this.minAmount = minAmount;
            }

            public int getPoints() {
                return points;
            }

            public void setPoints(int points) {
                this.points = points;
            }
        }

        public static class UnusualAmount {

            private java.math.BigDecimal multiplier = new java.math.BigDecimal("3");
            private java.math.BigDecimal minAmount = new java.math.BigDecimal("200");
            private java.time.Duration lookback = java.time.Duration.ofDays(30);
            private int minHistory = 3;
            private int points = 15;

            public java.math.BigDecimal getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(java.math.BigDecimal multiplier) {
                this.multiplier = multiplier;
            }

            public java.math.BigDecimal getMinAmount() {
                return minAmount;
            }

            public void setMinAmount(java.math.BigDecimal minAmount) {
                this.minAmount = minAmount;
            }

            public java.time.Duration getLookback() {
                return lookback;
            }

            public void setLookback(java.time.Duration lookback) {
                this.lookback = lookback;
            }

            public int getMinHistory() {
                return minHistory;
            }

            public void setMinHistory(int minHistory) {
                this.minHistory = minHistory;
            }

            public int getPoints() {
                return points;
            }

            public void setPoints(int points) {
                this.points = points;
            }
        }
    }

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
