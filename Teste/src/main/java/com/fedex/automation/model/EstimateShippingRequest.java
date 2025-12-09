package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the Estimate Shipping Methods API request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstimateShippingRequest {

    @JsonProperty("address")
    private Address address;

    @JsonProperty("productionLocation")
    private Object productionLocation;

    @JsonProperty("isPickup")
    private boolean isPickup;

    @JsonProperty("reRate")
    private boolean reRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomAttribute {
        @JsonProperty("attribute_code")
        private String attributeCode;

        @JsonProperty("value")
        private Object value;

        @JsonProperty("label")
        private String label;
    }
}