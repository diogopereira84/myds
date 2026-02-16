package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.graphql.GraphqlRequestBody;
import com.fedex.automation.service.fedex.client.CatalogApiClient;
import com.fedex.automation.service.fedex.strategy.ProductFilterStrategy;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final ProductFilterStrategy productFilter;

    @Autowired(required = false)
    private RequestSpecification defaultRequestSpec;

    // Adobe I/O Endpoint from your curl
    private static final String ADOBE_GRAPHQL_URL = "https://catalog-service-sandbox.adobe.io/graphql";

    /**
     * Searches for a 1P product using Adobe I/O GraphQL and finds the EXACT name match.
     * Criteria: Name must strictly match the keyword (ignoring case) to avoid picking test/partial matches.
     */
    public String search1PProduct(String keyword) {
        log.info("Searching for 1P Product with keyword: {}", keyword);

        String query = "query productSearch {" +
                "  productSearch(" +
                "    filter: [" +
                "      { attribute: \"shared_catalogs\", in: [\"3\"] }," +
                "      { attribute: \"is_pending_review\", in: [\"0\",\"2\",\"3\"] }" +
                "    ]," +
                "    phrase: \"" + keyword + "\"," +
                "    page_size: 10" +
                "  ) {" +
                "    items {" +
                "      product {" +
                "        sku" +
                "        name" +
                "      }" +
                "    }" +
                "  }" +
                "}";

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("variables", new HashMap<>());

        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Magento-Environment-Id", "5944b5ac-9e23-431a-a75e-880417ddb256")
                .header("Magento-Store-Code", "main_website_store")
                .header("Magento-Store-View-Code", "default")
                .header("Magento-Website-Code", "base")
                .header("Origin", "https://staging2.office.fedex.com")
                .header("Referer", "https://staging2.office.fedex.com/")
                .header("X-Api-Key", "c62c679a32e045229e33fcd27694edca")
                .header("X-Request-Id", "7196ba99-968c-4031-81e5-bad04b6ebb08")
                .body(payload)
                .post(ADOBE_GRAPHQL_URL);

        JsonPath jsonPath = response.jsonPath();

        // Extract list of products
        List<Map<String, Object>> items = jsonPath.getList("data.productSearch.items");

        if (items == null || items.isEmpty()) {
            fail("No 1P products found for keyword: " + keyword);
        }

        // FILTER LOGIC: Find the item where name matches 'keyword' exactly
        String foundSku = null;
        for (Map<String, Object> item : items) {
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            String name = (String) product.get("name");
            String sku = (String) product.get("sku");

            log.debug("Checking item: Name='{}', SKU='{}'", name, sku);

            if (name != null && name.equalsIgnoreCase(keyword)) {
                foundSku = sku;
                break;
            }
        }

        if (foundSku == null) {
            log.warn("No exact name match found for '{}'. Defaulting to first item as fallback.", keyword);
            foundSku = (String) ((Map<String, Object>) items.get(0).get("product")).get("sku");
        }

        log.info("Resolved 1P SKU: {}", foundSku);
        assertNotNull(foundSku, "Could not resolve SKU for 1P product");

        return foundSku;
    }

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