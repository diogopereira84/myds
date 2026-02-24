package com.fedex.automation.model.fedex.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuHierarchyResponse {

    private List<ProductMenuDetail> productMenuDetails;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductMenuDetail {
        private String id;
        private String name;
        private String productId;
    }
}