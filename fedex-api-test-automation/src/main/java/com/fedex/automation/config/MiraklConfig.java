package com.fedex.automation.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class MiraklConfig {

    @Value("${mirakl.ApiKey}")
    private String apiKey;

    @Value("${mirakl.base.url}")
    private String baseUrl;

    @Value("${endpoint.mirakl.offers}")
    private String offersEndpoint;
}