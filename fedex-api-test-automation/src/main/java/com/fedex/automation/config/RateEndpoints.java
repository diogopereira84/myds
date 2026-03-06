package com.fedex.automation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateEndpoints {

    private final String productRate;

    public RateEndpoints(@Value("${endpoint.rate.product}") String productRate) {
        this.productRate = productRate;
    }

    public String productRate() {
        return productRate;
    }
}

