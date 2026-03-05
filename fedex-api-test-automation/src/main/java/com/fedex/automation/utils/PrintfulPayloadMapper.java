package com.fedex.automation.utils;

import com.fedex.automation.model.printful.PrintfulVariant;
import io.cucumber.datatable.DataTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PrintfulPayloadMapper {

    private PrintfulPayloadMapper() {}

    public static List<PrintfulVariant> mapDataTableToVariants(DataTable dataTable) {
        List<Map<String, String>> variantRows = dataTable.asMaps(String.class, String.class);
        List<PrintfulVariant> variantMap = new ArrayList<>();

        for (Map<String, String> row : variantRows) {
            String price = row.get("price");

            // Strictly enforce that price must be provided
            if (price == null || price.trim().isEmpty()) {
                throw new IllegalArgumentException("A 'price' column is required in the data table for Printful variants but was not found.");
            }

            variantMap.add(PrintfulVariant.builder()
                    .variantId(Integer.parseInt(row.get("variantId")))
                    .size(row.get("size"))
                    .amount(Integer.parseInt(row.get("amount")))
                    .retail_discounted_price(price)
                    .bulkDiscountPrice(price)
                    .priceDifferenceFromOriginalBulkDiscountPrice("0.00")
                    .priceDifferenceFromMainVariant("0.00")
                    .build());
        }
        return variantMap;
    }
}