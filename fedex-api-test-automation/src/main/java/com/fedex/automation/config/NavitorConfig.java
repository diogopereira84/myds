package com.fedex.automation.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class NavitorConfig {

    @Value("${navitor.base.url}")
    private String baseUrl;

    @Value("${navitor.api.key}")
    private String apiKey;

    @Value("${navitor.store.id}")
    private String storeId;
}
