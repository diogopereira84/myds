package com.fedex.automation.model.fedex;

import java.util.Collections;
import java.util.Map;

public record CatalogItemCandidate(String sku, String name, Map<String, String> attributes) {

    public CatalogItemCandidate {
        attributes = (attributes == null) ? Map.of() : Collections.unmodifiableMap(attributes);
    }

    public String attribute(String key) {
        return attributes.getOrDefault(key, "");
    }
}
