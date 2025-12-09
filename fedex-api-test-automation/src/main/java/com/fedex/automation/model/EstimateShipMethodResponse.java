package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstimateShipMethodResponse {

    @JsonProperty("carrier_code")
    private String carrierCode;

    @JsonProperty("method_code")
    private String methodCode;

    @JsonProperty("carrier_title")
    private String carrierTitle;

    @JsonProperty("method_title")
    private String methodTitle;

    private BigDecimal amount;

    @JsonProperty("base_amount")
    private BigDecimal baseAmount;

    private Boolean available;

    @JsonProperty("price_incl_tax")
    private BigDecimal priceInclTax;

    @JsonProperty("price_excl_tax")
    private BigDecimal priceExclTax;

    @JsonProperty("offer_id")
    private String offerId;

    private String title;

    private String selected;

    @JsonProperty("selected_code")
    private String selectedCode;

    @JsonProperty("item_id")
    private String itemId;

    @JsonProperty("shipping_type_label")
    private String shippingTypeLabel;

    /**
     * Geralmente string humana no Magento
     */
    @JsonProperty("deliveryDate")
    private String deliveryDate;

    @JsonProperty("deliveryDateText")
    private String deliveryDateText;

    private Boolean marketplace;

    @JsonProperty("seller_id")
    private String sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("surcharge_amount")
    private String surchargeAmount;

    @JsonProperty("extension_attributes")
    private ExtensionAttributes extensionAttributes;

    private Address address;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtensionAttributes {
        private Boolean fastest;
        private Boolean cheapest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {

        @JsonProperty("countryId")
        private String countryId;

        @JsonProperty("regionId")
        private String regionId;

        @JsonProperty("regionCode")
        private String regionCode;

        private String region;

        private List<String> street;

        private String company;
        private String telephone;

        @JsonProperty("postcode")
        private String postcode;

        private String city;
        private String firstname;
        private String lastname;

        @JsonProperty("customAttributes")
        private List<CustomAttribute> customAttributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomAttribute {

        @JsonProperty("attribute_code")
        private String attributeCode;

        /**
         * Pode vir String/Boolean/etc
         */
        private Object value;

        private String label;
    }
}
