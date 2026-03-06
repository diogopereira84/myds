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
        if (variantRows.isEmpty()) {
            throw new IllegalArgumentException("Printful variants table must include at least one row.");
        }
        List<PrintfulVariant> variantMap = new ArrayList<>();

        for (Map<String, String> row : variantRows) {
            String variantId = required(row, "variantId");
            String size = required(row, "size");
            String amount = required(row, "amount");
            String price = required(row, "price");

            variantMap.add(PrintfulVariant.builder()
                    .variantId(parseInt(variantId, "variantId"))
                    .size(size)
                    .amount(parseInt(amount, "amount"))
                    .retailDiscountedPrice(price)
                    .bulkDiscountPrice(price)
                    .priceDifferenceFromOriginalBulkDiscountPrice("0.00")
                    .priceDifferenceFromMainVariant("0.00")
                    .build());
        }
        return variantMap;
    }

    private static String required(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required column: " + key);
        }
        return value.trim();
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for " + fieldName + ": " + value, ex);
        }
    }
}