package com.fedex.automation.service.fedex.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for 1P (FedEx Office) Products.
 * ID: "1P"
 * Criteria: product.name must EXACTLY match the targetProductName (case-insensitive).
 */
@Slf4j
@Component("1P")
public class OnePProductFilter implements ProductFilterStrategy {

    @Override
    public boolean isValid(JsonNode itemNode, String targetProductName) {
        // Access 'product' -> 'name' directly (based on your 1P_Response structure)
        String actualName = itemNode.path("product").path("name").asText("");

        boolean match = actualName.equalsIgnoreCase(targetProductName);

        if (match) {
            log.info("1P Strategy Match: '{}' matches target '{}'", actualName, targetProductName);
        }

        return match;
    }
}