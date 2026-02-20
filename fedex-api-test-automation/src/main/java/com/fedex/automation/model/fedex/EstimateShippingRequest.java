package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL) // Fix: Prevents null fields from being sent
public class EstimateShippingRequest {

    // Removed "pickup" field to match UI sample exactly (only "isPickup" is needed)

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
    @JsonInclude(JsonInclude.Include.NON_NULL) // Fix: Prevents "label": null
    public static class CustomAttribute {
        @JsonProperty("attribute_code")
        private String attributeCode;

        @JsonProperty("value")
        private Object value;

        @JsonProperty("label")
        private String label;
    }
}