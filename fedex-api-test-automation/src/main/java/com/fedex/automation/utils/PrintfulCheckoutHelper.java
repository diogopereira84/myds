package com.fedex.automation.utils;

import com.fedex.automation.model.printful.PrintfulProductPricesResponse;
import com.fedex.automation.model.printful.PrintfulVariant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PrintfulCheckoutHelper {

    public static List<PrintfulVariant> buildVariantMapWithPricing(
            List<PrintfulVariant> baseVariants,
            PrintfulProductPricesResponse pricesResponse,
            String targetTechnique) {

        List<PrintfulVariant> finalVariantMap = new ArrayList<>();
        BigDecimal mainVariantPrice = null;

        // Loop through our mapped variants (S, M, L, XL, etc.)
        for (int i = 0; i < baseVariants.size(); i++) {
            PrintfulVariant baseVariant = baseVariants.get(i);
            int currentId = baseVariant.getVariantId();

            // Find this specific ID in the pricing response
            var matchedPriceObj = pricesResponse.getVariants().stream()
                    .filter(v -> v.getId() == currentId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find pricing data for Variant ID: " + currentId));

            // Extract the price for the specific technique (e.g. "dtg")
            var techniquePriceObj = matchedPriceObj.getTechniques().stream()
                    .filter(t -> t.getTechniqueKey().equalsIgnoreCase(targetTechnique))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find technique '" + targetTechnique + "' for Variant ID: " + currentId));

            BigDecimal currentPrice = new BigDecimal(techniquePriceObj.getRetailDiscountedPrice())
                    .setScale(2, RoundingMode.HALF_UP);

            // The FIRST item in the array (e.g., Size 'S') acts as the "Main Variant" for price diff calculations
            if (i == 0) {
                mainVariantPrice = currentPrice;
            }

            // Calculate the exact price difference (e.g. 5XL is usually more expensive than S)
            BigDecimal diff = currentPrice.subtract(mainVariantPrice).setScale(2, RoundingMode.HALF_UP);

            finalVariantMap.add(PrintfulVariant.builder()
                    .variantId(currentId)
                    .size(baseVariant.getSize())
                    .amount(baseVariant.getAmount())
                    .retailDiscountedPrice(currentPrice.toPlainString())
                    .bulkDiscountPrice(currentPrice.toPlainString())
                    .priceDifferenceFromOriginalBulkDiscountPrice(diff.toPlainString())
                    .priceDifferenceFromMainVariant(diff.toPlainString())
                    .build());
        }

        return finalVariantMap;
    }
}