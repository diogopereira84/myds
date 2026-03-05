package com.fedex.automation.model.mirakl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MiraklShopOffersResponse {

    private List<MiraklOffer> offers;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MiraklOffer {

        @JsonProperty("offer_id")
        private Long offerId;

        @JsonProperty("product_sku")
        private String productSku;

        @JsonProperty("shop_sku")
        private String shopSku;

        @JsonProperty("price")
        private Double price;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("shop_id")
        private String shopId;
    }
}