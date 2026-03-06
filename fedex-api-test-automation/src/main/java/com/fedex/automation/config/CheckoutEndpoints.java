package com.fedex.automation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CheckoutEndpoints {

    private final String estimate;
    private final String deliveryRate;
    private final String createQuote;
    private final String payRate;
    private final String submitOrder;
    private final String encryptionKey;

    public CheckoutEndpoints(
            @Value("${endpoint.shipping.estimate}") String estimate,
            @Value("${endpoint.shipping.deliveryrate}") String deliveryRate,
            @Value("${endpoint.quote.create}") String createQuote,
            @Value("${endpoint.pay.rate}") String payRate,
            @Value("${endpoint.order.submit}") String submitOrder,
            @Value("${endpoint.delivery.encryptionkey}") String encryptionKey
    ) {
        this.estimate = estimate;
        this.deliveryRate = deliveryRate;
        this.createQuote = createQuote;
        this.payRate = payRate;
        this.submitOrder = submitOrder;
        this.encryptionKey = encryptionKey;
    }

    public String estimate() {
        return estimate;
    }

    public String deliveryRate() {
        return deliveryRate;
    }

    public String createQuote() {
        return createQuote;
    }

    public String payRate() {
        return payRate;
    }

    public String submitOrder() {
        return submitOrder;
    }

    public String encryptionKey() {
        return encryptionKey;
    }
}

