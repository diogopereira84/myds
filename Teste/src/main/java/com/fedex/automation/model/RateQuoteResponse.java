package com.fedex.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * DTO (Data Transfer Object) for deserializing a Rate Quote API response.
 * The '@JsonIgnoreProperties(ignoreUnknown = true)' annotation ensures that
 * fields present in the JSON but not in this class are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RateQuoteResponse {

    @JsonProperty("rateQuote")
    private RateQuote rateQuote;

    /**
     * Inner class representing the core 'rateQuote' object.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RateQuote {
        @JsonProperty("currency")
        private String currency;

        @JsonProperty("rateQuoteDetails")
        private List<RateQuoteDetail> rateQuoteDetails;
    }

    /**
     * Inner class representing the detailed information for a specific rate quote.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RateQuoteDetail {
        @JsonProperty("rateQuoteId")
        private String rateQuoteId;

        @JsonProperty("productLines")
        private List<ProductLine> productLines;

        @JsonProperty("totalAmount")
        private Double totalAmount;
    }

    /**
     * Inner class representing a specific product or service line item within the quote.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductLine {
        @JsonProperty("instanceId")
        private String instanceId;

        @JsonProperty("productId")
        private String productId;

        @JsonProperty("unitQuantity")
        private Integer unitQuantity;

        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;
    }
}