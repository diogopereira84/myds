package com.fedex.automation.model.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmitOrderRequest {

    @JsonProperty("paymentData")
    private
    String paymentData; // JSON String
    @JsonProperty("encCCData")
    private String encCCData;
    @JsonProperty("pickupData")
    private Object pickupData;
    @JsonProperty("useSiteCreditCard")
    private boolean useSiteCreditCard;
    @JsonProperty("selectedProductionId")
    private Object selectedProductionId;
    @JsonProperty("g-recaptcha-response")
    private String gRecaptchaResponse;

}