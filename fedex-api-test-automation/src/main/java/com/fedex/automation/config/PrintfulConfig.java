package com.fedex.automation.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class PrintfulConfig {

    @Value("${printful.api.base.url}")
    private String printfulApiBaseUrl;

    @Value("${printful.web.base.url}")
    private String printfulWebBaseUrl;

    @Value("${printful.api.key}")
    private String printfulApiToken;

    @Value("${printful.store.id}")
    private String printfulStoreId;

    @Value("${printful.s3.url}")
    private String printfulS3Url;

    @Value("${apparel.base.url}")
    private String customApparelBaseUrl;

    @Value("${apparel.endpoint.validate.session}")
    private String validateSessionEndpoint;

    @Value("${apparel.endpoint.generate.nonce}")
    private String generateNonceEndpoint;

    @Value("${apparel.endpoint.order.checkout}")
    private String orderCheckoutEndpoint;

    @Value("${apparel.retry.timeout.seconds:10}")
    private int retryTimeoutSeconds;

    @Value("${apparel.retry.interval.seconds:2}")
    private int retryIntervalSeconds;
}
