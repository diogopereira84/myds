package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.context.TestContext;
import org.springframework.stereotype.Component;

@Component
public class DocumentDimensionResolver {

    private final TestContext testContext;

    public DocumentDimensionResolver(TestContext testContext) {
        this.testContext = testContext;
    }

    public String resolveConfiguredDimension(String propertyName, String fallback) {
        JsonNode productNode = testContext.getCurrentConfiguredProductNode();
        if (productNode == null) {
            return fallback;
        }

        JsonNode properties = productNode.path("properties");
        if (!properties.isArray()) {
            return fallback;
        }

        for (JsonNode property : properties) {
            if (propertyName.equals(property.path("name").asText())) {
                String value = property.path("value").asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return fallback;
    }
}

