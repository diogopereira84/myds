package com.fedex.automation.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class NavitorConfig {

    @Value("${printful.base.url}")
    private String baseUrl;

    @Value("${printful.api.key}")
    private String apiKey;

    @Value("${printful.store.id}")
    private String storeId;

    @Value("${printful.s3.url}")
    private String baseUrlS3;
}
