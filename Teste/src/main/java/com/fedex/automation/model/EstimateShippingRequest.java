package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the Estimate Shipping Methods API request body.
 * Matches the structure required by: /default/rest/default/V1/guest-carts/{cartId}/estimate-shipping-methods
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstimateShippingRequest {

    @JsonProperty("address")
    private Address address;

    @JsonProperty("productionLocation")
    private Object productionLocation; // Can be null or complex object

    @JsonProperty("isPickup")
    private boolean isPickup;

    @JsonProperty("reRate")
    private boolean reRate;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @JsonProperty("street")
        private List<String> street;

        @JsonProperty("city")
        private String city;

        @JsonProperty("region_id")
        private String regionId;

        @JsonProperty("region")
        private String region;

        @JsonProperty("country_id")
        private String countryId;

        @JsonProperty("postcode")
        private String postcode;

        @JsonProperty("firstname")
        private String firstname;

        @JsonProperty("lastname")
        private String lastname;

        @JsonProperty("company")
        private String company;

        @JsonProperty("telephone")
        private String telephone;

        @JsonProperty("custom_attributes")
        private List<CustomAttribute> customAttributes;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomAttribute {
        @JsonProperty("attribute_code")
        private String attributeCode;

        /**
         * Value can be String (e.g., email) or Boolean (e.g., residence_shipping)
         */
        @JsonProperty("value")
        private Object value;

        @JsonProperty("label")
        private String label; // Optional field seen in some attributes
    }
}