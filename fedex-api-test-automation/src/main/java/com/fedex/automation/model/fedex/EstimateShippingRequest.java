package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstimateShippingRequest {

    // Added field for Step 3 fix
    @JsonProperty("pickup")
    private boolean pickup;

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