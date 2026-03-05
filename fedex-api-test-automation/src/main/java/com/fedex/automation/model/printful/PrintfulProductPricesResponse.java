package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrintfulProductPricesResponse {
    private List<VariantPrice> variants;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantPrice {
        private int id; // The variantId (e.g. 22118)
        private List<TechniquePrice> techniques;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TechniquePrice {
        @JsonProperty("technique_key")
        private String techniqueKey; // e.g. "dtg"

        @JsonProperty("retail_discounted_price")
        private String retailDiscountedPrice; // e.g. "23.43"
    }
}