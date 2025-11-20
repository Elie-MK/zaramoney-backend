package com.zekodnix.zaramoney.config;

import com.cloudinary.Cloudinary;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    private final ApplicationProperties applicationProperties;

    public CloudinaryConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    public Cloudinary cloudinary() {
        ApplicationProperties.Cloudinary cloud = applicationProperties.getCloudinary();
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloud.getCloudname());
        config.put("api_key", cloud.getApikey());
        config.put("api_secret", cloud.getApisecret());
        return new Cloudinary(config);
    }
}
