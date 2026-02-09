package com.fedex.automation.service.fedex.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Iterator;

@Component
public class MiraklB2BFilter implements ProductFilterStrategy {

    @Override
    public boolean isValid(JsonNode itemNode) {
        JsonNode attributes = itemNode.path("productView").path("attributes");

        // We initialize these as false
        boolean isMiraklSync = false;
        boolean hasMiraklShopId = false;

        Iterator<JsonNode> it = attributes.elements();
        while (it.hasNext()) {
            JsonNode attr = it.next();
            String name = attr.path("name").asText();
            String value = attr.path("value").asText();

            // CRITERIA 1: Must be synced with Mirakl
            if ("mirakl_sync".equals(name) && "yes".equalsIgnoreCase(value)) {
                isMiraklSync = true;
            }

            // CRITERIA 2: Must have a valid Shop ID (Vendor)
            if ("mirakl_shop_ids".equals(name) && value != null && !value.isEmpty()) {
                hasMiraklShopId = true;
            }
        }

        // REMOVED: is_catalog_product check.
        // Reason: Some valid B2B products (like office supplies) do not carry this flag,
        // but 'mirakl_sync' is sufficient to prove they are valid and transactable.

        return isMiraklSync && hasMiraklShopId;
    }
}