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
    // jhipster-needle-application-properties-property-class
}
