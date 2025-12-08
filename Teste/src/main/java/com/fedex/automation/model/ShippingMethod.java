package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO (Data Transfer Object) representing a single Shipping Method option returned by an API.
 * The '@JsonIgnoreProperties(ignoreUnknown = true)' annotation ensures that
 * fields present in the JSON but not defined here are safely ignored during deserialization.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingMethod {

    @JsonProperty("carrier_code")
    private String carrierCode; // Internal code for the shipping carrier (e.g., 'fedex')

    @JsonProperty("method_code")
    private String methodCode; // Internal code for the specific shipping service (e.g., 'express_saver')

    @JsonProperty("carrier_title")
    private String carrierTitle; // Display name for the carrier (e.g., 'FedEx')

    @JsonProperty("method_title")
    private String methodTitle; // Display name for the service method (e.g., 'Express Saver')

    @JsonProperty("amount")
    private Double amount; // The price of the shipping method in local currency

    @JsonProperty("base_amount")
    private Double baseAmount; // The price in the store's base currency

    @JsonProperty("available")
    private Boolean available; // Indicates whether this method is currently available for the order

    @JsonProperty("offer_id")
    private String offerId; // A unique identifier for the specific shipping offer

    @JsonProperty("title")
    private String title; // A generic title for the shipping method (may combine carrier and method titles)

    @JsonProperty("seller_name")
    private String sellerName; // Name of the seller offering this shipping option (useful in marketplace setups)
}