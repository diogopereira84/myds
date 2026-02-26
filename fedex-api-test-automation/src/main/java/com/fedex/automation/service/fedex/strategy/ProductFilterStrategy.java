package com.fedex.automation.service.fedex.strategy;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProductFilterStrategy {
    /**
     * Evaluates if a catalog item matches the specific seller model criteria.
     * @param itemNode The JSON node representing a single product item.
     * @param targetProductName The name requested in the test (used for exact matching in 1P).
     * @return true if valid.
     */
    boolean isValid(JsonNode itemNode, String targetProductName);
}