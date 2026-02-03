package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.config.AdobeConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification; // Import for the injected spec
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    @Autowired
    private AdobeConfig adobeConfig;

    @Autowired
    private ObjectMapper objectMapper;

    // Use the centralized RequestSpecification if you have the RestConfig set up,
    // otherwise strictly stick to the logic required for the fix.
    // Based on previous context, I'll assume you might want to use the defaultRequestSpec
    // if available, but to be safe and "only fix the parse issue",
    // I will use a standard given() unless you specifically need the custom filter here.
    // However, for consistency with your architecture:
    @Autowired(required = false)
    private RequestSpecification defaultRequestSpec;

    public String searchProductSku(String productName) {
        log.info("--- Searching Catalog for Product: '{}' ---", productName);

        // FIX 1: Updated Query matches 'response111' structure (requesting product { sku })
        // We also keep the 'filter' logic from your curl to ensure we get the correct B2B item.
        String query = """
            {
              "query": "query productSearch { productSearch(phrase: \\"%s\\", filter: [ { attribute: \\"shared_catalogs\\", in: [\\"3\\"] }, { attribute: \\"is_pending_review\\", in: [\\"0\\",\\"2\\",\\"3\\"] } ], current_page: 1, page_size: 1) { items { product { sku name } } } }",
              "variables": {}
            }
            """.formatted(productName);

        RequestSpecification spec = (defaultRequestSpec != null) ? given().spec(defaultRequestSpec) : given();

        Response response = spec
                .contentType(ContentType.JSON)
                .header("X-Api-Key", adobeConfig.getApiKey())
                .header("Magento-Environment-Id", adobeConfig.getEnvironmentId())
                .header("Magento-Website-Code", adobeConfig.getWebsiteCode())
                .header("Magento-Store-Code", adobeConfig.getStoreCode())
                .header("Magento-Store-View-Code", adobeConfig.getStoreViewCode())
                .body(query)
                .post(adobeConfig.getGraphqlEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();

        try {
            JsonNode root = objectMapper.readTree(response.asString());

            if (root.has("errors")) {
                throw new RuntimeException("Adobe API Error: " + root.path("errors").toPrettyString());
            }

            JsonNode items = root.path("data")
                    .path("productSearch")
                    .path("items");

            if (items.isEmpty()) {
                throw new RuntimeException("No products found for phrase: " + productName + ". Check Environment ID.");
            }

            // FIX 2: Correct Parsing Path based on 'response111'
            // Old: items.get(0).path("productView").path("sku") -> Wrong
            // New: items.get(0).path("product").path("sku")     -> Correct
            String sku = items.get(0).path("product").path("sku").asText();

            // Fallback safety check (optional, but good for stability)
            if (sku == null || sku.isEmpty()) {
                log.warn("SKU not found in 'product' node, checking 'productView' as fallback...");
                sku = items.get(0).path("productView").path("sku").asText();
            }

            if (sku == null || sku.isEmpty()) {
                throw new RuntimeException("SKU not found in Catalog response. Response: " + items.toPrettyString());
            }

            log.info("Found SKU: {}", sku);
            return sku;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Catalog response", e);
        }
    }
}