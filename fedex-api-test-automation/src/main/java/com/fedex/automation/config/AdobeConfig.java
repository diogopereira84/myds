package com.fedex.automation.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AdobeConfig {

    @Value("${sandbox.public.ApiKey}")
    private String apiKey;

    @Value("${sandbox.environmentId}")
    private String environmentId;

    @Value("${endpoint.adobe.catalog.graphql}")
    private String graphqlEndpoint;

    // Scopes discovered during debugging
    private final String websiteCode = "base";
    private final String storeCode = "main_website_store";
    private final String storeViewCode = "default";
}