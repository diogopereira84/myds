package com.fedex.automation.service.fedex.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Iterator;

/**
 * Strategy for 3P (Marketplace) Products.
 * ID: "3P"
 * Criteria: Attributes 'mirakl_sync' must be 'yes' and 'mirakl_shop_ids' must exist.
 */
@Component("3P")
public class ThreePProductFilter implements ProductFilterStrategy {

    @Override
    public boolean isValid(JsonNode itemNode, String targetProductName) {
        // 3P validation relies on attributes, usually ignoring strict name equality
        // because the search API handles the phrase match.
        JsonNode attributes = itemNode.path("productView").path("attributes");

        boolean isMiraklSync = false;
        boolean hasMiraklShopId = false;

        Iterator<JsonNode> it = attributes.elements();
        while (it.hasNext()) {
            JsonNode attr = it.next();
            String name = attr.path("name").asText();
            String value = attr.path("value").asText();

            if ("mirakl_sync".equals(name) && "yes".equalsIgnoreCase(value)) {
                isMiraklSync = true;
            }

            if ("mirakl_shop_ids".equals(name) && value != null && !value.isEmpty()) {
                hasMiraklShopId = true;
            }
        }

        return isMiraklSync && hasMiraklShopId;
    }
}