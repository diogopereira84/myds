package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fedex.automation.model.EstimateShipMethodResponse.ExtensionAttributes;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * DTO for the Create Quote API request payload.
 * Uses a decoupled hierarchy to strictly enforce field presence per section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateQuotePayload {

    @JsonProperty("addressInformation")
    private AddressInformation addressInformation;

    @JsonProperty("rateapi_response")
    private String rateApiResponse;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressInformation {
        @JsonProperty("shipping_address")
        private QuoteShippingAddress shippingAddress;

        @JsonProperty("billing_address")
        private QuoteBillingAddress billingAddress;

        @JsonProperty("shipping_method_code")
        private String shippingMethodCode;

        @JsonProperty("shipping_carrier_code")
        private String shippingCarrierCode;

        @JsonProperty("shipping_detail")
        private QuoteShippingDetail shippingDetail;
    }

    // --- 1. Base Address (Common to ALL addresses) ---
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class QuoteAddressBase {
        @JsonProperty("countryId")
        private String countryId;
        @JsonProperty("regionId")
        private String regionId;
        @JsonProperty("regionCode")
        private String regionCode;
        @JsonProperty("region")
        private String region;
        @JsonProperty("street")
        private List<String> street;
        @JsonProperty("company")
        private String company;
        @JsonProperty("telephone")
        private String telephone;
        @JsonProperty("postcode")
        private String postcode;
        @JsonProperty("city")
        private String city;
        @JsonProperty("firstname")
        private String firstname;
        @JsonProperty("lastname")
        private String lastname;
        @JsonProperty("customAttributes")
        private List<QuoteCustomAttribute> customAttributes;
    }

    // --- 2. Detail Address (Strictly Base fields ONLY) ---
    // Used in 'shipping_detail'. Guarantees no 'alt' or 'is_alternate' fields.
    @EqualsAndHashCode(callSuper = false)
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteDetailAddress extends QuoteAddressBase {
    }

    // --- 3. Full Address (Base + Alternate fields) ---
    @EqualsAndHashCode(callSuper = false)
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class QuoteFullAddress extends QuoteAddressBase {
        @JsonProperty("altFirstName")
        private String altFirstName;
        @JsonProperty("altLastName")
        private String altLastName;
        @JsonProperty("altPhoneNumber")
        private String altPhoneNumber;
        @JsonProperty("altEmail")
        private String altEmail;
        @JsonProperty("altPhoneNumberext")
        private String altPhoneNumberext;

        @JsonProperty("is_alternate")
        private Boolean alternate;
    }

    // --- 4. Main Shipping Address (Full Address) ---
    @EqualsAndHashCode(callSuper = false)
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteShippingAddress extends QuoteFullAddress {
    }

    // --- 5. Main Billing Address (Full Address + saveInAddressBook) ---
    @EqualsAndHashCode(callSuper = false)
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteBillingAddress extends QuoteFullAddress {
        // Must ALWAYS appear, even if null
        @JsonProperty("saveInAddressBook")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Object saveInAddressBook;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuoteCustomAttribute {
        @JsonProperty("attribute_code")
        private String attributeCode;
        @JsonProperty("value")
        private Object value;
        @JsonProperty("label")
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteShippingDetail {
        @JsonProperty("carrier_code")
        private String carrierCode;
        @JsonProperty("method_code")
        private String methodCode;
        @JsonProperty("carrier_title")
        private String carrierTitle;
        @JsonProperty("method_title")
        private String methodTitle;
        @JsonProperty("amount")
        private Object amount;
        @JsonProperty("base_amount")
        private Object baseAmount;
        @JsonProperty("available")
        private Boolean available;
        @JsonProperty("price_incl_tax")
        private Object priceInclTax;
        @JsonProperty("price_excl_tax")
        private Object priceExclTax;
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

        // Uses the strict 'Detail' address class
        @JsonProperty("address")
        private QuoteDetailAddress address;

        @JsonProperty("fedexShipReferenceId")
        private String fedexShipReferenceId;

        @JsonProperty("productionLocation")
        private String productionLocation;
    }
}