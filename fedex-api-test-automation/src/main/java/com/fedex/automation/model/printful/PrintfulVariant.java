package com.fedex.automation.model.printful;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintfulVariant {
    private Integer variantId;
    private String size;
    private Integer amount;
    private String retail_discounted_price;
    private String bulkDiscountPrice;
    private String priceDifferenceFromOriginalBulkDiscountPrice;
    private String priceDifferenceFromMainVariant;
}