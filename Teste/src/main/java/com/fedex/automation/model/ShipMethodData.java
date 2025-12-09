package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the 'ship_method_data' JSON string required by the Delivery Rate API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipMethodData {

    @JsonProperty("carrier_code")
    private String carrierCode;

    @JsonProperty("method_code")
    private String methodCode;

    @JsonProperty("carrier_title")
    private String carrierTitle;

    @JsonProperty("method_title")
    private String methodTitle;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("base_amount")
    private Double baseAmount;

    @JsonProperty("available")
    private Boolean available;

    @JsonProperty("price_incl_tax")
    private Double priceInclTax;

    @JsonProperty("price_excl_tax")
    private Double priceExclTax;

    @JsonProperty("offer_id")
    private String offerId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("selected")
    private String selected;

    @JsonProperty("selected_code")
    private String selectedCode;

    @JsonProperty("item_id")
    private String itemId;

    @JsonProperty("shipping_type_label")
    private String shippingTypeLabel;

    @JsonProperty("deliveryDate")
    private String deliveryDate;

    @JsonProperty("deliveryDateText")
    private String deliveryDateText;

    @JsonProperty("marketplace")
    private Boolean marketplace;

    @JsonProperty("seller_id")
    private String sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("surcharge_amount")
    private String surchargeAmount;

    @JsonProperty("extension_attributes")
    private ExtensionAttributes extensionAttributes;

    @JsonProperty("address")
    private Address address;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtensionAttributes {
        @JsonProperty("fastest")
        private Boolean fastest;

        @JsonProperty("cheapest")
        private Boolean cheapest;
    }

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