package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.graphql.GraphqlRequestBody;
import com.fedex.automation.service.fedex.client.CatalogApiClient;
import com.fedex.automation.service.fedex.strategy.ProductFilterStrategy;
import io.restassured.specification.RequestSpecification;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final RequestSpecification defaultRequestSpec;

    // Spring injects all implementations into this map keyed by their @Component name ("1P", "3P")
    private final Map<String, ProductFilterStrategy> filterStrategies;

    private static final String GRAPHQL_QUERY_TEMPLATE = """
            query productSearch {
                productSearch(
                    filter: [
                        { attribute: "shared_catalogs", in: ["3"] },
                        { attribute: "is_pending_review", in: ["0", "2", "3"] }
                    ],
                    phrase: "%s",
                    page_size: 20
                ) {
                    items {
                        product {
                            sku name
                        }
                        productView {
                            attributes { label name value }
                        }
                    }
                }
            }
            """;

    /**
     * Searches for a product SKU using the specified Seller Model strategy.
     * @param productName The product name to search for.
     * @param sellerModel "1P" or "3P". Defaults to "3P" if null.
     * @return The found SKU.
     */
    public String searchProductSku(String productName, String sellerModel) {
        String model = (sellerModel == null || sellerModel.isEmpty()) ? "3P" : sellerModel.toUpperCase();
        log.info("--- Searching Catalog for '{}' using Strategy: {} ---", productName, model);

        // 1. Resolve Strategy
        ProductFilterStrategy strategy = filterStrategies.get(model);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for Seller Model: " + model);
        }

        // 2. Execute API Call
        String rawQuery = GRAPHQL_QUERY_TEMPLATE.formatted(productName);
        GraphqlRequestBody requestBody = new GraphqlRequestBody(rawQuery, Collections.emptyMap());
        Response response = apiClient.searchProducts(requestBody, defaultRequestSpec);

        // 3. Filter Results
        return extractValidSku(response, productName, strategy);
    }

    // Overload for backward compatibility (defaults to 3P)
    public String searchProductSku(String productName) {
        return searchProductSku(productName, "3P");
    }

    private String extractValidSku(Response response, String productName, ProductFilterStrategy strategy) {
        try {
            if (response.statusCode() != 200) {
                log.error("API Error Body: {}", response.asString());
                throw new RuntimeException("Catalog API failed with status: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.asString());
            if (root.has("errors")) {
                throw new RuntimeException("Adobe API Error: " + root.path("errors").toPrettyString());
            }

            JsonNode items = root.path("data").path("productSearch").path("items");

            if (items.isEmpty()) {
                throw new RuntimeException("No products found for phrase: " + productName);
            }

            // 4. Iterate and Apply Strategy
            for (JsonNode item : items) {
                if (strategy.isValid(item, productName)) {
                    String sku = item.path("product").path("sku").asText();
                    String name = item.path("product").path("name").asText();
                    log.info("SKU Resolved: {} (Name: {})", sku, name);
                    return sku;
                }
            }

            throw new RuntimeException("Products found, but none matched the criteria for strategy: " + strategy.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Error processing catalog response", e);
            throw new RuntimeException("Failed to process Catalog response", e);
        }
    }
}