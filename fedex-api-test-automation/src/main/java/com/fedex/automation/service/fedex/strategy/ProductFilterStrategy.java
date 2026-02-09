package com.fedex.automation.service.fedex.strategy;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProductFilterStrategy {
    boolean isValid(JsonNode itemNode);
}