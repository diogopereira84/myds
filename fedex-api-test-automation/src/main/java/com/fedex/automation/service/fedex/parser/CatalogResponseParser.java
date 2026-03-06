package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.CatalogItemCandidate;
import com.fedex.automation.service.fedex.exception.CatalogErrorCode;
import com.fedex.automation.service.fedex.exception.CatalogOperationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogResponseParser {

    private final ObjectMapper objectMapper;

    public CatalogResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CatalogItemCandidate> extractCandidatesOrThrow(String responseBody, String productName) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("errors") && root.path("errors").isArray() && !root.path("errors").isEmpty()) {
                throw new CatalogOperationException(
                        CatalogErrorCode.UPSTREAM_GRAPHQL_ERROR,
                        "Adobe API Error: " + root.path("errors").toPrettyString()
                );
            }

            JsonNode items = root.path("data").path("productSearch").path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new CatalogOperationException(
                        CatalogErrorCode.NO_RESULTS,
                        "No products found for phrase: " + productName
                );
            }

            return toCandidates(items);
        } catch (CatalogOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CatalogOperationException(CatalogErrorCode.PARSE_ERROR, "Failed to parse catalog response payload.", e);
        }
    }

    private List<CatalogItemCandidate> toCandidates(JsonNode items) {
        List<CatalogItemCandidate> candidates = new ArrayList<>();
        for (JsonNode item : items) {
            String sku = item.path("product").path("sku").asText("");
            String name = item.path("product").path("name").asText("");
            Map<String, String> attributes = extractAttributes(item.path("productView").path("attributes"));
            candidates.add(new CatalogItemCandidate(sku, name, attributes));
        }
        return candidates;
    }

    private Map<String, String> extractAttributes(JsonNode attributesNode) {
        Map<String, String> attributes = new HashMap<>();
        if (!attributesNode.isArray()) {
            return attributes;
        }

        for (JsonNode attribute : attributesNode) {
            String key = attribute.path("name").asText("");
            if (!key.isBlank()) {
                attributes.put(key, attribute.path("value").asText(""));
            }
        }
        return attributes;
    }
}
