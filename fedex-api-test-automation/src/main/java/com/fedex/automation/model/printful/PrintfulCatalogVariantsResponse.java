package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrintfulCatalogVariantsResponse {
    private List<CatalogVariant> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CatalogVariant {
        private int id;
        private String size;
        private String color;
    }
}