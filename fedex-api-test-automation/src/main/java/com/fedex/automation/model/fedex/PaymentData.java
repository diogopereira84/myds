package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fedex.automation.model.fedex.CreateQuotePayload.QuoteCustomAttribute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentData {
    @JsonProperty("loginValidationKey")
    private String loginValidationKey;
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    @JsonProperty("year")
    private String year;
    @JsonProperty("expire")
    private String expire;
    @JsonProperty("nameOnCard")
    private String nameOnCard;
    @JsonProperty("number")
    private String number;
    @JsonProperty("cvv")
    private String cvv;
    @JsonProperty("isBillingAddress")
    private boolean isBillingAddress;
    @JsonProperty("isFedexAccountApplied")
    private boolean isFedexAccountApplied;
    @JsonProperty("fedexAccountNumber")
    private String fedexAccountNumber;
    @JsonProperty("creditCardType")
    private String creditCardType;
    @JsonProperty("billingAddress")
    private PaymentBillingAddress billingAddress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentBillingAddress {
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
        private boolean isAlternate;

        @JsonProperty("address")
        private String address;
        @JsonProperty("addressTwo")
        private String addressTwo;
    }
}