package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.graphql.GraphqlRequestBody;
import com.fedex.automation.service.fedex.client.CatalogApiClient;
import com.fedex.automation.service.fedex.strategy.ProductFilterStrategy;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final ProductFilterStrategy productFilter;

    @Autowired(required = false)
    private RequestSpecification defaultRequestSpec;

    // FIXED QUERY: 'facets' moved outside of 'items'
    private static final String GRAPHQL_QUERY_TEMPLATE = """
            query productSearch {
                productSearch(
                    filter: [
                        { attribute: "shared_catalogs", in: ["3"] },
                        { attribute: "is_pending_review", in: ["0", "2", "3"] }
                    ],
                    phrase: "%s",
                    page_size: 10
                ) {
                    total_count
                    items {
                        product {
                            id sku name __typename canonical_url
                            small_image { url }
                            image { url label }
                            thumbnail { url label }
                            price_range {
                                minimum_price {
                                    final_price { value currency }
                                }
                            }
                        }
                        productView {
                            attributes { label name value }
                        }
                    }
                    facets { 
                        title 
                    }
                }
            }
            """;

    public String searchProductSku(String productName) {
        log.info("--- Searching Catalog for Product: '{}' ---", productName);

        // 1. Prepare the raw GraphQL string
        String rawQuery = GRAPHQL_QUERY_TEMPLATE.formatted(productName);

        // 2. Wrap it in the object. Jackson will handle the escaping and formatting.
        GraphqlRequestBody requestBody = new GraphqlRequestBody(rawQuery, Collections.emptyMap());

        // 3. Send Request
        Response response = apiClient.searchProducts(requestBody, defaultRequestSpec);

        // 4. Validate & Extract (Logic remains the same)
        return extractValidSku(response, productName);
    }

    private String extractValidSku(Response response, String productName) {
        try {
            if (response.statusCode() != 200) {
                // Log the body to see the exact error from Adobe
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

            for (JsonNode item : items) {
                if (productFilter.isValid(item)) {
                    String sku = item.path("product").path("sku").asText();
                    String name = item.path("product").path("name").asText();
                    log.info("Valid B2B Product Found: '{}' | SKU: {}", name, sku);
                    return sku;
                }
            }

            throw new RuntimeException("Products found, but none matched the valid B2B criteria.");

        } catch (Exception e) {
            log.error("Error processing catalog response", e);
            throw new RuntimeException("Failed to process Catalog response", e);
        }
    }
}