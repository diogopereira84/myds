package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrintfulCatalogResponse {
    private List<CatalogProduct> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CatalogProduct {
        private int id;
        private String name;

        @JsonProperty("is_discontinued")
        private boolean isDiscontinued;

        private List<String> sizes;
        private List<CatalogColor> colors;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CatalogColor {
        private String name;
    }
}