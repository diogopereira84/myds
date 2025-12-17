package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class Address {
    @JsonProperty("street")
    private List<String> street;

    @JsonProperty("city")
    private String city;

    @JsonProperty("region_id")
    private String regionId;

    @JsonProperty("region")
    private String region;

    @JsonProperty("country_id")
    private String countryId;

    @JsonProperty("postcode")
    private String postcode;

    @JsonProperty("firstname")
    private String firstname;

    @JsonProperty("lastname")
    private String lastname;

    @JsonProperty("company")
    private String company;

    @JsonProperty("telephone")
    private String telephone;

    @JsonProperty("custom_attributes")
    private List<EstimateShippingRequest.CustomAttribute> customAttributes;
}