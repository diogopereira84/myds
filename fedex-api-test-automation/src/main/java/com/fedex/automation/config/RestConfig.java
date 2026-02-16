package com.fedex.automation.config;

import com.fedex.automation.utils.CurlLoggingFilter;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfig {

    @Autowired
    private CurlLoggingFilter curlLoggingFilter;

    @Bean
    public RequestSpecification defaultRequestSpec() {
        RequestSpecBuilder builder = new RequestSpecBuilder();

        // 1. Relaxed SSL
        builder.setRelaxedHTTPSValidation();

        // 2. Add our custom Logging Filter
        builder.addFilter(curlLoggingFilter);

        return builder.build();
    }

    @PostConstruct
    public void configureGlobalRestAssured() {
        // Apply filter globally so it catches all requests, even those not using the bean directly
        RestAssured.filters(curlLoggingFilter);

        // Disable default RestAssured logging to prevent double-logging
        RestAssured.config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails());
    }
}