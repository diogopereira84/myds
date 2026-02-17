package com.fedex.automation.config;

import com.fedex.automation.utils.CurlLoggingFilter;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RestConfig {

    private final CurlLoggingFilter curlLoggingFilter;

    @Bean
    public RequestSpecification defaultRequestSpec() {
        return new RequestSpecBuilder()
                .setRelaxedHTTPSValidation()
                .addFilter(curlLoggingFilter) // Apply our smart filter bean
                .build();
    }

    @PostConstruct
    public void configureGlobalRestAssured() {
        // Apply filter globally as a safety net for any 'given()' calls created without the bean
        RestAssured.filters(curlLoggingFilter);

        // Disable default RestAssured logging to prevent double-logging (we handle it in the filter)
        RestAssured.config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails());
    }
}