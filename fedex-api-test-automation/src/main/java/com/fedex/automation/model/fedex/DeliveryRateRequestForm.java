package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryRateRequestForm {

    private String firstname;
    private String lastname;
    private String email;
    private String telephone;

    @JsonProperty("ship_method")
    private String shipMethod;

    private String zipcode;

    @JsonProperty("region_id")
    private String regionId;

    private String city;

    /**
     * In the real form, this becomes street[]=A & street[]=
     */
    @Builder.Default
    private List<String> street = new ArrayList<>();

    private String company;

    @JsonProperty("is_residence_shipping")
    private Boolean isResidenceShipping;

    /**
     * This field in the form is a JSON string.
     * We keep it as an object and serialize it in the client.
     */
    @JsonProperty("ship_method_data")
    private EstimateShipMethodResponse shipMethodData;

    @JsonProperty("third_party_carrier_code")
    private String thirdPartyCarrierCode;

    @JsonProperty("third_party_method_code")
    private String thirdPartyMethodCode;

    @JsonProperty("first_party_carrier_code")
    private String firstPartyCarrierCode;

    @JsonProperty("first_party_method_code")
    private String firstPartyMethodCode;

    @JsonProperty("location_id")
    private String locationId;
}