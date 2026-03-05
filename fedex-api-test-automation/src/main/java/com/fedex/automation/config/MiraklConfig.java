package com.fedex.automation.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class MiraklConfig {

    @Value("${mirakl.base.url}")
    private String baseUrl;

    @Value("${mirakl.api.key}")
    private String apiKey;

    @Value("${mirakl.endpoint.offers}")
    private String offersEndpoint;

    @Value("${mirakl.endpoint.shop.offers}")
    private String shopOffersEndpoint;

    @Value("${mirakl.shop.id.printful}")
    private String printfulShopId;
}