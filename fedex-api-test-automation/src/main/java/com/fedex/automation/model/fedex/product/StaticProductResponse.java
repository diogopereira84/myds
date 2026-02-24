package com.fedex.automation.model.fedex.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StaticProductResponse {
    private StaticProduct product;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaticProduct {
        private String id;
        private String name;
        private int version;
        private List<ProductFeature> features;
        private List<ProductProperty> properties;
        private List<ContentRequirement> contentRequirements;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductFeature {
        private String id;
        private String name;
        private String defaultChoiceId;
        private List<ProductChoice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductChoice {
        private String id;
        private String name;
        private List<ProductProperty> properties;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductProperty {
        private String id;
        private String name;
        private String value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentRequirement {
        private String id;
        private String name;
        private String purpose;
    }
}