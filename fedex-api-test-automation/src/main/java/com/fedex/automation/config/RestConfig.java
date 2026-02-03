package com.fedex.automation.config;

import com.fedex.automation.utils.CurlLoggingFilter;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfig {

    @Value("${logging.curl.enabled:false}")
    private boolean logCurl;

    /**
     * Creates a pre-configured Request Specification.
     * All global configurations (logging, timeouts, relaxed SSL) go here.
     */
    @Bean
    public RequestSpecification defaultRequestSpec() {
        RequestSpecBuilder builder = new RequestSpecBuilder();

        // 1. Global: Relaxed HTTPS (ignores SSL errors)
        builder.setRelaxedHTTPSValidation();

        // 2. Global: Apply cURL Filter if enabled in properties
        if (logCurl) {
            builder.addFilter(new CurlLoggingFilter());
        }

        return builder.build();
    }
}